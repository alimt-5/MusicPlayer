package com.example.musicplayer.musicFeature.domain.model

data class PlaybackState(
    val currentTrackId: Long? = null,
    val isPlaying: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL
)
