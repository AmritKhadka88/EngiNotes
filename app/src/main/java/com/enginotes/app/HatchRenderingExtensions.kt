package com.enginotes.app

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent

// Hatch pattern rendering (both the ~50 built-in procedural patterns and user-added/snipped
// custom bitmap hatches) plus the canvas-region snip tool that feeds custom hatches. Split out
// of DrawingView.kt as extension functions on DrawingView, same technique used for MainActivity's
// extractions. Needed `actions`, `drawActionItem`, `getOrLoadFillBitmap`, `exportWindowStart`,
// and `exportWindowEnd` widened from private to internal in DrawingView.kt — these are shared
// core rendering/hit-testing infrastructure used throughout the class, not hatch-specific, so
// widening them doesn't change what they do, just which files can reach them.
//
// customHatchBitmapCache stays declared as a real DrawingView property (see near `actions` in
// DrawingView.kt) rather than living here, since an extension function can't hold its own
// stored state — there's no way to add real storage to a class from outside it in Kotlin.
//
// NOTE ON SPLITTING DrawingView.kt FURTHER: this file only exists because hatch-rendering turned
// out to be genuinely separable — a handful of clear dependencies, not deeply entangled with
// the rest of the rendering engine. The core drawing/hit-testing code (drawActionItem's dispatch,
// getBounds, getOrBuildLayout, eraser splitting, etc.) is NOT similarly separable: nearly every
// function there calls several others, and pulling any one piece out would mean widening dozens
// of members with a much higher chance of missing one and breaking the build blind (no compiler
// available in this workflow). That core is staying as one file rather than forcing a risky split.

internal fun DrawingView.loadCustomHatchBitmap(path: String): Bitmap? {
        customHatchBitmapCache[path]?.let { return it }
        return try {
            android.graphics.BitmapFactory.decodeFile(path)?.also { customHatchBitmapCache[path] = it }
        } catch (e: Exception) { null }
    }

internal fun DrawingView.drawHatchPattern(canvas: Canvas, item: FillItem) {
        if (item.hatchPattern == null && item.customHatchPath == null) return
        val l = item.x; val t = item.y; val r = item.x + item.w; val b = item.y + item.h
        item.hatchRenderCache?.let { canvas.drawBitmap(it, null, RectF(l, t, r, b), null); return }

        val customPath = item.customHatchPath
        if (customPath != null) {
            // Custom (user-added or user-snipped) hatch: tile the bitmap itself instead of
            // running a procedural line-drawing. Uses its own original colors as-is — no
            // tinting via hatchColor, since that would be surprising for an arbitrary photo
            // and isn't needed for a snip (which is already just the ink color it was drawn in).
            val tile = loadCustomHatchBitmap(customPath) ?: return
            val bw = item.w.toInt().coerceAtLeast(1); val bh = item.h.toInt().coerceAtLeast(1)
            val hatchBmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            val hc = Canvas(hatchBmp)
            // hatchScale controls the tile's rendered size (world units per tile edge) — same
            // "bigger number = broader/coarser pattern" convention as the procedural patterns.
            val tileSizePx = (96f * item.hatchScale).coerceAtLeast(8f)
            val shader = android.graphics.BitmapShader(tile, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            val m = android.graphics.Matrix(); val sc = tileSizePx / tile.width.toFloat().coerceAtLeast(1f); m.setScale(sc, sc)
            shader.setLocalMatrix(m)
            hc.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
            val bmpMask = getOrLoadFillBitmap(item)
            if (bmpMask != null) {
                val maskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
                hc.drawBitmap(bmpMask, null, android.graphics.Rect(0, 0, bw, bh), maskPaint)
                item.hatchRenderCache = hatchBmp
            }
            canvas.drawBitmap(hatchBmp, null, RectF(l, t, r, b), null)
            return
        }

        val hp = item.hatchPattern ?: return
        // Was rebuilding the entire hatch (potentially thousands of drawLine/drawCircle calls
        // for dense/small hatch spacing, PLUS a fresh Bitmap.createBitmap allocation) on every
        // single frame this fill was on screen — exactly the same "recompute forever" mistake
        // fixed earlier for Calligraphy/Fountain strokes. hatchPattern/hatchColor/hatchScale
        // are only ever set once at creation (no live hatch-editing exists), so caching the
        // final composited result is safe indefinitely.
        val s = 8f * item.hatchScale
        val sw = 1.5f  // stroke width in world coords
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.hatchColor; style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.hatchColor; style = Paint.Style.FILL }

        // Draw hatch into offscreen bitmap clipped to the flood-fill shape mask
        val bmp = getOrLoadFillBitmap(item)
        if (bmp != null) {
            // Render hatch to a temp bitmap at the same world size, then composite using flood-fill alpha as mask
            val bw = item.w.toInt().coerceAtLeast(1); val bh = item.h.toInt().coerceAtLeast(1)
            val hatchBmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            val hc = Canvas(hatchBmp)
            // Offset into local coords (0,0 = top-left of bounding box)
            val lp = Paint(p).apply { strokeWidth = sw }
            val lsp = Paint(sp)
            drawHatchLocal(hc, hp, 0f, 0f, bw.toFloat(), bh.toFloat(), s, lp, lsp)
            // Use flood-fill bitmap as alpha mask: multiply alpha channels
            val maskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
            hc.drawBitmap(bmp, null, android.graphics.Rect(0, 0, bw, bh), maskPaint)
            item.hatchRenderCache = hatchBmp
            canvas.drawBitmap(hatchBmp, null, RectF(l, t, r, b), null)
        } else {
            // Shape mask still loading asynchronously — draw directly this one time WITHOUT
            // caching (caching now would lock in a render made before the mask was ready).
            canvas.save(); canvas.clipRect(l, t, r, b)
            drawHatchLocal(canvas, hp, l, t, r, b, s, p, sp)
            canvas.restore()
        }
    }

internal fun DrawingView.drawHatchLocal(canvas: Canvas, hp: HatchPattern, l: Float, t: Float, r: Float, b: Float, s: Float, p: Paint, sp: Paint) {
        val w = r - l; val h = b - t
        when (hp) {
            HatchPattern.HATCH_45 -> { var x = l - h; while (x < r + h) { canvas.drawLine(x, t, x+h, b, p); x += s } }
            HatchPattern.HATCH_135 -> { var x = l - h; while (x < r + h) { canvas.drawLine(x+h, t, x, b, p); x += s } }
            HatchPattern.HATCH_90 -> { var x = l; while (x < r) { canvas.drawLine(x, t, x, b, p); x += s } }
            HatchPattern.HATCH_0 -> { var y = t; while (y < b) { canvas.drawLine(l, y, r, y, p); y += s } }
            HatchPattern.HATCH_CROSS -> { var x = l; while (x < r) { canvas.drawLine(x, t, x, b, p); x += s }; var y = t; while (y < b) { canvas.drawLine(l, y, r, y, p); y += s } }
            HatchPattern.HATCH_DIAGONAL_CROSS -> {
                var x = l - h; while (x < r + h) { canvas.drawLine(x, t, x+h, b, p); x += s }
                x = l - h; while (x < r + h) { canvas.drawLine(x+h, t, x, b, p); x += s }
            }
            HatchPattern.CONCRETE -> {
                val rand = java.util.Random(42); var y = t
                while (y < b) { canvas.drawLine(l, y, r, y, p); y += s * 1.5f }
                for (i in 0..((w*h/s/s*3).toInt())) { canvas.drawCircle(l + rand.nextFloat()*w, t + rand.nextFloat()*h, s*0.15f, sp) }
            }
            HatchPattern.STEEL -> { var x = l - h; while (x < r + h) { canvas.drawLine(x, t, x+h, b, p); canvas.drawLine(x+s*0.3f, t, x+h+s*0.3f, b, p); x += s * 2f } }
            HatchPattern.EARTH -> {
                var y = t; while (y < b) { canvas.drawLine(l, y, r, y, p); y += s }
                val rand = java.util.Random(42); for (i in 0..(w*h/s/s).toInt()) canvas.drawCircle(l+rand.nextFloat()*w, t+rand.nextFloat()*h, s*0.12f, sp)
            }
            HatchPattern.SAND -> { val rand = java.util.Random(42); for (i in 0..(w*h/s/s*5).toInt()) canvas.drawCircle(l+rand.nextFloat()*w, t+rand.nextFloat()*h, s*0.08f, sp) }
            HatchPattern.ROCK -> {
                val rand = java.util.Random(42); var y = t
                while (y < b) { var x = l; while (x < r) { val path2 = android.graphics.Path()
                    path2.moveTo(x+rand.nextFloat()*s, y+rand.nextFloat()*s*0.5f); path2.lineTo(x+s+rand.nextFloat()*s*0.3f, y+rand.nextFloat()*s*0.5f)
                    path2.lineTo(x+s*1.2f, y+s+rand.nextFloat()*s*0.3f); path2.lineTo(x+rand.nextFloat()*s*0.5f, y+s+rand.nextFloat()*s*0.3f); path2.close()
                    canvas.drawPath(path2, p); x += s*1.5f }; y += s*1.5f }
            }
            HatchPattern.GRAVEL -> { val rand = java.util.Random(42); for (i in 0..(w*h/s/s*3).toInt()) { val cx=l+rand.nextFloat()*w; val cy=t+rand.nextFloat()*h; val rs=s*0.2f+rand.nextFloat()*s*0.3f; canvas.drawOval(android.graphics.RectF(cx-rs,cy-rs*0.6f,cx+rs,cy+rs*0.6f),p) } }
            HatchPattern.WOOD_GRAIN -> {
                var y = t; while (y < b) { val path2 = android.graphics.Path(); path2.moveTo(l, y); var x = l
                    while (x < r) { path2.quadTo(x+s*1.5f, y+s*0.3f*kotlin.math.sin((x-l)/w.toFloat()*Math.PI.toFloat()*4), x+s*3f, y); x+=s*3f }
                    canvas.drawPath(path2, p); y += s*0.8f }
            }
            HatchPattern.WOOD_END -> { val cx=(l+r)/2f; val cy=(t+b)/2f; val maxR=minOf(w,h)/2f; var rad=s; while(rad<maxR){canvas.drawOval(android.graphics.RectF(cx-rad,cy-rad*0.6f,cx+rad,cy+rad*0.6f),p);rad+=s*0.8f} }
            HatchPattern.BRICK -> {
                var y = t; var row = 0; while (y < b) { val offset=if(row%2==0)0f else s*1.5f; canvas.drawLine(l,y,r,y,p); var x=l-offset; while(x<r){canvas.drawLine(x,y,x,y+s,p);x+=s*3f}; y+=s; row++ }
            }
            HatchPattern.BLOCK -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s}; var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s} }
            HatchPattern.GLASS -> { val ap=p.alpha; p.alpha=(ap*0.6f).toInt(); var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*0.8f}; p.alpha=(ap*0.3f).toInt(); x=l-h; while(x<r+h){canvas.drawLine(x+h,t,x,b,p);x+=s*1.6f}; p.alpha=ap }
            HatchPattern.INSULATION -> {
                var y=t; while(y<b){ val path2=android.graphics.Path(); path2.moveTo(l,y); var x=l; while(x<r){path2.quadTo(x+s*0.5f,y-s*0.4f,x+s,y);path2.quadTo(x+s*1.5f,y+s*0.4f,x+s*2f,y);x+=s*2f}; canvas.drawPath(path2,p); y+=s*1.2f }
            }
            HatchPattern.RUBBER -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.5f}; var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*3f} }
            HatchPattern.PLASTIC -> { var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*0.6f} }
            HatchPattern.CERAMIC -> { var y=t; var row=0; while(y<b){var x=l+if(row%2==0)0f else s; while(x<r){canvas.drawRect(x,y,x+s*1.8f,y+s*1.8f,p);x+=s*2f};y+=s*2f;row++} }
            HatchPattern.FIBERGLASS -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawLine(x,y,x+s*0.5f,y+s,p);x+=s*0.4f};y+=s*1.5f} }
            HatchPattern.FOAM -> { val rand=java.util.Random(42); var y=t; while(y<b){var x=l; while(x<r){val rs=s*(0.3f+rand.nextFloat()*0.4f);canvas.drawCircle(x+rand.nextFloat()*s*0.5f,y+rand.nextFloat()*s*0.5f,rs,p);x+=s*1.2f};y+=s*1.2f} }
            HatchPattern.MEMBRANE -> { var y=t; val origSW=p.strokeWidth; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.4f}; p.strokeWidth=origSW*2f; var y2=t+s; while(y2<b){canvas.drawLine(l,y2,r,y2,p);p.strokeWidth=origSW;y2+=s*3f} }
            HatchPattern.ALUMINUM -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*1.5f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*3f} }
            HatchPattern.COPPER -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);canvas.drawLine(x+s*0.5f,t,x+h+s*0.5f,b,p);x+=s*2f} }
            HatchPattern.IRON -> { val origSW=p.strokeWidth; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.6f}; p.strokeWidth=origSW*2f; var y2=t; while(y2<b){canvas.drawLine(l,y2,r,y2,p);y2+=s*3f}; p.strokeWidth=origSW }
            HatchPattern.BRONZE -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f} }
            HatchPattern.TITANIUM -> { var x=l-h; while(x<r+h){canvas.drawLine(x+h,t,x,b,p);x+=s*1.2f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2.4f} }
            HatchPattern.GOLD_HATCH -> { var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*0.8f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.8f} }
            HatchPattern.COMPACTED_FILL -> {
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s}
                val rand=java.util.Random(42); for(i in 0..(w*h/s/s*2).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;canvas.drawLine(cx-s*0.3f,cy,cx+s*0.3f,cy+s*0.3f,p)}
            }
            HatchPattern.LOOSE_FILL -> { val rand=java.util.Random(42); for(i in 0..(w*h/s/s*4).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;val a=rand.nextFloat()*Math.PI.toFloat()*2f;canvas.drawLine(cx,cy,cx+kotlin.math.cos(a)*s*0.5f,cy+kotlin.math.sin(a)*s*0.5f,p)} }
            HatchPattern.CLAY -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.4f} }
            HatchPattern.SILT -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*0.3f}; val rand=java.util.Random(42); for(i in 0..(w*h/s/s*2).toInt())canvas.drawCircle(l+rand.nextFloat()*w,t+rand.nextFloat()*h,s*0.06f,sp) }
            HatchPattern.PEAT -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s}; val rand=java.util.Random(42); for(i in 0..(w*h/s/s*3).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;canvas.drawOval(android.graphics.RectF(cx-s*0.2f,cy-s*0.1f,cx+s*0.2f,cy+s*0.1f),sp)} }
            HatchPattern.CHALK -> { var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s*0.7f} }
            HatchPattern.DOTS_FINE -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawCircle(x,y,s*0.08f,sp);x+=s*0.6f};y+=s*0.6f} }
            HatchPattern.DOTS_COARSE -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawCircle(x,y,s*0.2f,sp);x+=s};y+=s} }
            HatchPattern.HONEYCOMB -> {
                val hw=s*1.2f; val hh=s*1.4f; var row=0; var y=t
                while(y<b){var x=l+if(row%2==0)0f else hw*0.75f; while(x<r){val path2=android.graphics.Path()
                    path2.moveTo(x+hw*0.25f,y);path2.lineTo(x+hw*0.75f,y);path2.lineTo(x+hw,y+hh*0.3f);path2.lineTo(x+hw*0.75f,y+hh*0.6f);path2.lineTo(x+hw*0.25f,y+hh*0.6f);path2.lineTo(x,y+hh*0.3f);path2.close()
                    canvas.drawPath(path2,p);x+=hw*1.5f};y+=hh*0.6f;row++}
            }
            HatchPattern.BASKET_WEAVE -> {
                var y=t; var row=0; while(y<b){var x=l+if(row%2==0)0f else s; while(x<r){if(row%2==0){canvas.drawLine(x,y,x+s,y,p);canvas.drawLine(x,y,x,y+s,p)}else{canvas.drawLine(x,y+s,x+s,y+s,p);canvas.drawLine(x+s,y,x+s,y+s,p)};x+=s*2f};y+=s;row++}
            }
            HatchPattern.DIAMOND_GRID -> {
                var x=l-h; while(x<r+h){canvas.drawLine(x,t,x+h,b,p);x+=s}
                x=l-h; while(x<r+h){canvas.drawLine(x+h,t,x,b,p);x+=s}
            }
            HatchPattern.ZIGZAG -> {
                var y=t; while(y<b){val path2=android.graphics.Path();path2.moveTo(l,y);var x=l;var up=true; while(x<r){path2.lineTo(x+s,if(up)y-s*0.5f else y+s*0.5f);x+=s;up=!up};canvas.drawPath(path2,p);y+=s*1.2f}
            }
            HatchPattern.WAVE -> {
                var y=t; while(y<b){val path2=android.graphics.Path();path2.moveTo(l,y);var x=l; while(x<r){path2.quadTo(x+s*0.5f,y-s*0.5f,x+s,y);path2.quadTo(x+s*1.5f,y+s*0.5f,x+s*2f,y);x+=s*2f};canvas.drawPath(path2,p);y+=s*1.2f}
            }
            HatchPattern.HERRINGBONE -> {
                var y=t;var row=0; while(y<b){var x=l; while(x<r){if(row%2==0){canvas.drawLine(x,y,x+s,y+s,p);canvas.drawLine(x+s,y+s,x+s*2f,y,p)}else{canvas.drawLine(x,y+s,x+s,y,p);canvas.drawLine(x+s,y,x+s*2f,y+s,p)};x+=s*2f};y+=s;row++}
            }
            HatchPattern.SCALE -> {
                var y=t;var row=0; while(y<b){var x=l+if(row%2==0)0f else s; while(x<r){canvas.drawArc(android.graphics.RectF(x-s,y,x+s,y+s*2f),0f,-180f,false,p);x+=s*2f};y+=s;row++}
            }
            HatchPattern.CHAIN_LINK -> { var y=t; while(y<b){var x=l; while(x<r){canvas.drawOval(android.graphics.RectF(x-s*0.3f,y-s*0.5f,x+s*0.3f,y+s*0.5f),p);x+=s};y+=s} }
            HatchPattern.STIPPLE -> { val rand=java.util.Random(42); for(i in 0..(w*h/s/s*8).toInt())canvas.drawCircle(l+rand.nextFloat()*w,t+rand.nextFloat()*h,s*0.06f,sp) }
            HatchPattern.CONTOUR -> { var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f} }
            HatchPattern.WATER -> {
                var y=t; while(y<b){val path2=android.graphics.Path();path2.moveTo(l,y);var x=l; while(x<r){path2.quadTo(x+s*0.5f,y-s*0.3f,x+s,y);x+=s};canvas.drawPath(path2,p);y+=s*0.8f}
            }
            HatchPattern.CONCRETE_PRECAST -> {
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f}; var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*3f}
                val rand=java.util.Random(42); for(i in 0..(w*h/s/s).toInt())canvas.drawCircle(l+rand.nextFloat()*w,t+rand.nextFloat()*h,s*0.08f,sp)
            }
            HatchPattern.REBAR -> { var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*2f}; var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f} }
            HatchPattern.ASPHALT -> {
                val rand=java.util.Random(42); for(i in 0..(w*h/s/s*6).toInt()){val cx=l+rand.nextFloat()*w;val cy=t+rand.nextFloat()*h;canvas.drawCircle(cx,cy,s*0.05f+rand.nextFloat()*s*0.1f,sp)}
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*2f}
            }
            HatchPattern.PLYWOOD -> {
                var y=t;var layer=0; while(y<b){if(layer%2==0){var x=l; while(x<r){canvas.drawLine(x,y,x,y+s,p);x+=s*0.3f}}else{canvas.drawLine(l,y+s*0.5f,r,y+s*0.5f,p)};y+=s;layer++}
            }
            HatchPattern.DRYWALL -> {
                var y=t; while(y<b){canvas.drawLine(l,y,r,y,p);y+=s*3f}; var x=l; while(x<r){canvas.drawLine(x,t,x,b,p);x+=s*4f}
            }
        }
    }

internal fun DrawingView.handleHatchSnip(event: MotionEvent) {
        val wx = screenToWorldX(event.x); val wy = screenToWorldY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { exportWindowStart = Pair(wx, wy); exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_MOVE -> { exportWindowEnd = Pair(wx, wy); invalidate() }
            MotionEvent.ACTION_UP -> {
                val s = exportWindowStart ?: return; val e = exportWindowEnd ?: return
                val left = minOf(s.first, e.first); val top = minOf(s.second, e.second); val right = maxOf(s.first, e.first); val bottom = maxOf(s.second, e.second)
                exportWindowStart = null; exportWindowEnd = null; currentTool = Tool.SELECT; onInternalToolChange?.invoke(Tool.SELECT); invalidate()
                if (right - left > 20f && bottom - top > 20f) {
                    val bmp = snipHatchBitmap(left, top, right, bottom)
                    onHatchSnipSelected?.invoke(bmp, left, top, right, bottom)
                }
            }
        }
    }

    // Renders ONLY the strokes inside the given world-space rectangle onto a transparent
    // bitmap — no page background, no ruled lines, no paper color, no shape fills — so the
    // result is exactly the ink itself, tileable as a custom hatch. Reuses the same
    // includeFills=false convention already established by renderStrokesOnly() for "strokes
    // only" rendering, just cropped to a region instead of the whole view.
internal fun DrawingView.snipHatchBitmap(left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val w = (right - left).coerceAtLeast(1f).toInt(); val h = (bottom - top).coerceAtLeast(1f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(-left, -top)
        for (a in actions) drawActionItem(canvas, a, includeFills = false)
        return bmp
    }
