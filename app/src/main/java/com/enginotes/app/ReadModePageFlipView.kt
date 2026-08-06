package com.enginotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import com.eschao.android.widget.pageflip.OnPageFlipListener
import com.eschao.android.widget.pageflip.PageFlip
import com.eschao.android.widget.pageflip.PageFlipException
import com.eschao.android.widget.pageflip.PageFlipState
import java.util.concurrent.locks.ReentrantLock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Paper-like Read Mode's page-turn renderer, built on eschao/android-PageFlip
 * (https://github.com/eschao/android-PageFlip, Apache License 2.0) instead of the earlier
 * hand-rolled Canvas mesh-warp curl — a real GPU cylindrical-fold implementation rather than a
 * 2D approximation, which is what kept producing the mesh/shadow artifacts in the Canvas version
 * across many rounds of fixes.
 *
 * This class owns ONLY the GL surface and the touch-to-PageFlip wiring, mirroring the library's
 * own sample app's PageFlipView/PageRender/SinglePageRender pattern (merged into one class here
 * since — unlike the sample's book reader — Read Mode never needs double-page/spread mode, only
 * single pages). Actual page CONTENT still comes from DrawingView.renderPageContentBitmap(),
 * reusing the exact same rendering path as normal editing and PDF export; this view has no idea
 * how a page's content is drawn, only how to page-turn between bitmaps it's handed.
 */
class ReadModePageFlipView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer, OnPageFlipListener {

    companion object {
        private const val MSG_ENDED_DRAWING_FRAME = 1
        private const val DRAW_MOVING_FRAME = 0
        private const val DRAW_ANIMATING_FRAME = 1
        private const val DRAW_FULL_PAGE = 2
    }

    // --- Wiring set by the host (MainActivity) before this view is shown ---
    /** Renders page [index] (0-based) at the given pixel size; null if out of range/OOM. */
    var pageProvider: ((index: Int, w: Int, h: Int) -> Bitmap?)? = null
    /** Total number of pages available right now. */
    var pageCountProvider: (() -> Int)? = null
    /** Fired on the main thread whenever the settled page index changes, after a completed flip
     *  (not during the drag itself) — the host uses this to keep DrawingView.readPageIndex and
     *  any page-number UI in sync. */
    var onPageChanged: ((Int) -> Unit)? = null

    private val pageFlip: PageFlip = PageFlip(context).apply {
        setSemiPerimeterRatio(0.8f)
        setShadowWidthOfFoldEdges(5f, 60f, 0.3f)
        setShadowWidthOfFoldBase(5f, 80f, 0.4f)
        setPixelsOfMesh(10)
        enableAutoPage(false)
    }
    private val drawLock = ReentrantLock()
    // 0-based index of the page currently loaded on the library's FIRST texture — the library's
    // own page numbering is 1-based and reader-specific (used for "The First/Last Page" labels
    // in its sample), which doesn't map cleanly onto this app's 0-based page indices, so this
    // class tracks it independently and only talks to pageProvider/onPageChanged in 0-based terms.
    private var pageNo = 0
    private var drawCommand = DRAW_FULL_PAGE
    private var contentBitmap: Bitmap? = null
    // "Base" duration used when the release velocity is low/deliberate — the actual per-gesture
    // duration (computed in handleFingerUp below) scales this down for a fast swipe and clamps
    // back up toward it for a slow one, rather than every flip taking the same fixed time
    // regardless of how the user actually swiped.
    private var baseDurationMs = 400
    private var minDurationMs = 120
    private var velocityTracker: VelocityTracker? = null

    private val mainHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_ENDED_DRAWING_FRAME) {
            try {
                drawLock.lock()
                if (onEndedDrawing(msg.arg1)) requestRender()
            } finally { drawLock.unlock() }
        }
        true
    }

    init {
        setEGLContextClientVersion(2)
        pageFlip.setListener(this)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Jump straight to [index] with no flip animation — used when Read Mode is first opened or
     *  the page changes some other way (e.g. scrolling in normal edit mode before switching into
     *  Read Mode), as opposed to a finger-driven flip. Safe to call before the GL surface exists;
     *  it just primes the state that onSurfaceChanged/onDrawFrame pick up once it does. */
    fun resetToPage(index: Int) {
        try {
            drawLock.lock()
            pageNo = index.coerceAtLeast(0)
            drawCommand = DRAW_FULL_PAGE
        } finally { drawLock.unlock() }
        // deleteAllTextures() below calls glDeleteTextures directly, which needs the GL thread's
        // EGL context current — queueEvent is GLSurfaceView's standard mechanism for running GL
        // calls safely regardless of which thread resetToPage() itself was called from.
        queueEvent {
            try {
                drawLock.lock()
                pageFlip.firstPage?.deleteAllTextures()
                pageFlip.secondPage?.deleteAllTextures()
            } finally { drawLock.unlock() }
            requestRender()
        }
    }

    fun setBaseDurationMs(ms: Int) { baseDurationMs = ms.coerceAtLeast(50) }

    /** Releases the content bitmap and detaches this view's listener from the PageFlip instance.
     *  Call when Read Mode is closed / this view is removed, mirroring the sample's
     *  PageRender.release(). */
    fun releasePageFlip() {
        try {
            drawLock.lock()
            contentBitmap?.recycle(); contentBitmap = null
            pageFlip.setListener(null)
            velocityTracker?.recycle(); velocityTracker = null
        } finally { drawLock.unlock() }
    }

    // --- OnPageFlipListener ---
    override fun canFlipForward(): Boolean = pageNo < (pageCountProvider?.invoke() ?: 1) - 1
    // NOT a pure query — matches the library's own sample: starting a backward flip requires
    // moving the current page's texture into the "second texture" slot first (and marking first
    // unset), which is what makes onDrawFrame's "!page.isFirstTextureSet" check below correctly
    // fire and load the PREVIOUS page's content. Skipping this is exactly what silently broke
    // backward flips — the state machine still entered BACKWARD_FLIP correctly, but the old
    // texture never got swapped out, so the current page just peeled back over itself instead of
    // revealing the previous page. Plain field bookkeeping (no raw GL calls inside), so this is
    // safe to call from onFingerMove's calling thread (the main/UI thread, via onTouchEvent) same
    // as everywhere else this gets invoked from.
    override fun canFlipBackward(): Boolean {
        if (pageNo > 0) {
            pageFlip.firstPage?.setSecondTextureWithFirst()
            return true
        }
        return false
    }

    // --- Touch: same event-to-call mapping as the library's own sample (SampleActivity +
    // PageFlipView.onFingerDown/Move/Up), just read directly off onTouchEvent instead of through
    // a GestureDetector — simpler and avoids the detector's own fling/scroll-slop thresholds
    // adding any extra latency to what should feel like a direct finger-follow drag. ---
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                if (!pageFlip.isAnimating) pageFlip.onFingerDown(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (pageFlip.isAnimating) {
                    // Mid-animation: ignore further move input, matching the library sample.
                } else if (pageFlip.canAnimate(event.x, event.y)) {
                    handleFingerUp(event.x, event.y)
                } else if (pageFlip.onFingerMove(event.x, event.y)) {
                    try {
                        drawLock.lock()
                        drawCommand = DRAW_MOVING_FRAME
                    } finally { drawLock.unlock() }
                    requestRender()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                handleFingerUp(event.x, event.y)
                velocityTracker?.recycle(); velocityTracker = null
            }
        }
        return true
    }

    // Maps the finger's release speed onto an animation duration: fast swipe -> short duration
    // (page "flies" the rest of the way, feels snappy), slow/deliberate release -> stays close to
    // baseDurationMs. 1000px/s is roughly a brisk, deliberate swipe on most screen densities —
    // used as the reference point where duration bottoms out at minDurationMs; anything slower
    // scales back up toward baseDurationMs.
    private fun computeFlipDurationMs(): Int {
        val vt = velocityTracker ?: return baseDurationMs
        vt.computeCurrentVelocity(1000) // px/second
        val speed = kotlin.math.abs(vt.xVelocity).coerceAtLeast(1f)
        val fastSpeedReference = 1000f
        val frac = (speed / fastSpeedReference).coerceIn(0f, 1f)
        return (baseDurationMs - (baseDurationMs - minDurationMs) * frac).toInt().coerceAtLeast(minDurationMs)
    }

    private fun handleFingerUp(x: Float, y: Float) {
        if (!pageFlip.isAnimating) {
            pageFlip.onFingerUp(x, y, computeFlipDurationMs())
            var needsRender = false
            try {
                drawLock.lock()
                if (pageFlip.animating()) { drawCommand = DRAW_ANIMATING_FRAME; needsRender = true }
            } finally { drawLock.unlock() }
            if (needsRender) requestRender()
        }
    }

    // --- GLSurfaceView.Renderer (runs on the GL thread) ---
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try { pageFlip.onSurfaceCreated() } catch (e: PageFlipException) { /* surface unusable; next onDrawFrame calls are no-ops via null-checks below */ }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        try {
            pageFlip.onSurfaceChanged(width, height)
            try {
                drawLock.lock()
                contentBitmap?.recycle()
                val page = pageFlip.firstPage
                contentBitmap = Bitmap.createBitmap(
                    page.width().toInt().coerceAtLeast(1),
                    page.height().toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
            } finally { drawLock.unlock() }
        } catch (e: PageFlipException) { /* leave contentBitmap as-is; next resize retries */ }
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            drawLock.lock()
            pageFlip.deleteUnusedTextures()
            val page = pageFlip.firstPage ?: return
            when (drawCommand) {
                DRAW_MOVING_FRAME, DRAW_ANIMATING_FRAME -> {
                    if (pageFlip.flipState == PageFlipState.FORWARD_FLIP) {
                        if (!page.isSecondTextureSet) {
                            drawPageOnto(pageNo + 1)
                            contentBitmap?.let { page.setSecondTexture(it) }
                        }
                    } else if (!page.isFirstTextureSet) {
                        drawPageOnto(pageNo - 1)
                        contentBitmap?.let { page.setFirstTexture(it) }
                    }
                    pageFlip.drawFlipFrame()
                }
                DRAW_FULL_PAGE -> {
                    if (!page.isFirstTextureSet) {
                        drawPageOnto(pageNo)
                        contentBitmap?.let { page.setFirstTexture(it) }
                    }
                    pageFlip.drawPageFrame()
                }
            }
            val msg = Message.obtain(); msg.what = MSG_ENDED_DRAWING_FRAME; msg.arg1 = drawCommand
            mainHandler.sendMessage(msg)
        } finally { drawLock.unlock() }
    }

    // Renders page [index]'s content (via pageProvider, which calls back into
    // DrawingView.renderPageContentBitmap on the calling thread — pageProvider itself must be
    // safe to call from the GL thread, which it is: it only reads DrawingView's own item list
    // and draws into a fresh, local bitmap/canvas, no view state it isn't already restoring)
    // onto contentBitmap, ready to hand to the library as a texture source.
    private fun drawPageOnto(index: Int) {
        val bmp = contentBitmap ?: return
        val src = pageProvider?.invoke(index, bmp.width, bmp.height) ?: return
        Canvas(bmp).drawBitmap(src, 0f, 0f, null)
        if (src !== bmp) src.recycle()
    }

    // Runs on the MAIN thread (posted via mainHandler from the GL thread's onDrawFrame) — this is
    // where it's safe to advance pageNo and fire onPageChanged, unlike onDrawFrame itself.
    private fun onEndedDrawing(what: Int): Boolean {
        if (what != DRAW_ANIMATING_FRAME) return false
        return if (pageFlip.animating()) {
            drawCommand = DRAW_ANIMATING_FRAME
            true
        } else {
            when (pageFlip.flipState) {
                PageFlipState.END_WITH_FORWARD -> {
                    pageFlip.firstPage.setFirstTextureWithSecond()
                    pageNo++
                    onPageChanged?.invoke(pageNo)
                }
                PageFlipState.END_WITH_BACKWARD -> {
                    pageNo--
                    onPageChanged?.invoke(pageNo)
                }
                else -> { /* END_WITH_RESTORE, RESTORE_FLIP, etc — page didn't actually turn */ }
            }
            drawCommand = DRAW_FULL_PAGE
            true
        }
    }
}
