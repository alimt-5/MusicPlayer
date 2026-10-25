package com.example.musicplayer.musicFeature.domain.model

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val mediaUri: String,
    val durationMs: Long,
    val dateAddedMs: Long,
    val albumArtUri: String?,
    val filePath: String?,
    val normalizedTitle: String,
    val searchText: String
)
