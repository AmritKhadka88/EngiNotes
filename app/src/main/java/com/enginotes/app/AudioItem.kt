package com.enginotes.app

import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File

/**
 * Represents an audio recording placed on the canvas.
 * Serialised as: AUDIO\u0001path\u0001x\u0001y\u0001title\u0001durationMs
 */
class AudioItem(
    var path: String,
    var x: Float,
    var y: Float,
    var title: String = "Audio",
    var durationMs: Long = 0L
) {
    /** true while MediaPlayer is playing */
    var isPlaying: Boolean = false

    companion object {
        fun deserialize(line: String): AudioItem? {
            val p = line.split("\u0001")
            if (p.size < 5) return null
            return try {
                AudioItem(
                    path = p[1],
                    x = p[2].toFloat(),
                    y = p[3].toFloat(),
                    title = p[4],
                    durationMs = if (p.size > 5) p[5].toLongOrNull() ?: 0L else 0L
                )
            } catch (e: Exception) { null }
        }
    }

    fun serialize(): String =
        "AUDIO\u0001$path\u0001$x\u0001$y\u0001${title.replace("\u0001","_")}\u0001$durationMs\n"
}

/** Simple stateless helpers so MainActivity can record/play without holding state here */
object AudioHelper {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentPlayingItem: AudioItem? = null

    fun startRecording(outputFile: File): Boolean {
        return try {
            recorder?.release()
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    fun stopRecording(): Long {
        return try {
            recorder?.apply { stop(); release() }
            recorder = null
            // Return approx duration — caller will use MediaMetadataRetriever if needed
            0L
        } catch (e: Exception) {
            recorder = null; 0L
        }
    }

    fun togglePlay(item: AudioItem, onStop: () -> Unit) {
        if (item.isPlaying) {
            player?.apply { stop(); release() }
            player = null
            item.isPlaying = false
            currentPlayingItem = null
            onStop()
            return
        }
        // Stop any currently playing item
        currentPlayingItem?.let { it.isPlaying = false }
        player?.apply { stop(); release() }
        player = null

        try {
            val file = File(item.path)
            if (!file.exists()) { onStop(); return }
            player = MediaPlayer().apply {
                setDataSource(item.path)
                prepare()
                start()
                setOnCompletionListener {
                    item.isPlaying = false
                    currentPlayingItem = null
                    onStop()
                    release()
                    player = null
                }
            }
            item.isPlaying = true
            currentPlayingItem = item
        } catch (e: Exception) {
            e.printStackTrace()
            item.isPlaying = false
            onStop()
        }
    }

    fun releaseAll() {
        try { recorder?.apply { stop(); release() } } catch (e: Exception) {}
        try { player?.apply { stop(); release() } } catch (e: Exception) {}
        recorder = null; player = null
        currentPlayingItem?.let { it.isPlaying = false }
        currentPlayingItem = null
    }
}
