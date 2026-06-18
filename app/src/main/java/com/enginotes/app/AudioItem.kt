package com.enginotes.app

class AudioItem(
    var filePath: String,
    var title: String,
    var x: Float,
    var y: Float,
    var durationMs: Long = 0L
) {
    @Volatile var isPlaying: Boolean = false
}
