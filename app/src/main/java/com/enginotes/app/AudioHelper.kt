package com.enginotes.app

import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File

object AudioHelper {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var playingItem: AudioItem? = null

    fun startRecording(outputFile: File): Boolean {
        return try {
            stopRecording()
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
            false
        }
    }

    fun stopRecording(): Long {
        return try {
            val r = recorder ?: return 0L
            r.stop(); r.release(); recorder = null
            0L
        } catch (e: Exception) {
            recorder = null; 0L
        }
    }

    fun isRecording() = recorder != null

    fun togglePlay(item: AudioItem, onComplete: () -> Unit) {
        if (playingItem === item && player?.isPlaying == true) {
            player?.pause()
            item.isPlaying = false
            onComplete()
            return
        }
        stopAll()
        try {
            player = MediaPlayer().apply {
                setDataSource(item.filePath)
                prepare()
                start()
                setOnCompletionListener {
                    item.isPlaying = false
                    playingItem = null
                    onComplete()
                }
            }
            item.isPlaying = true
            playingItem = item
            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
            item.isPlaying = false
            playingItem = null
        }
    }

    fun stopAll() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        playingItem?.isPlaying = false
        playingItem = null
    }

    fun releaseAll() {
        stopAll()
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    fun isPlaying(item: AudioItem) = playingItem === item && player?.isPlaying == true
}
