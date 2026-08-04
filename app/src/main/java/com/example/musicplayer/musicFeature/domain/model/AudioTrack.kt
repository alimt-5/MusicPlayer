package com.example.musicplayer.musicFeature.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val mediaUri: String,
    val duration: Long,
    val path: String,
    val dateAdded: Long = 0L,
    val albumArtUri: String? = null
)