package com.example.musicplayer.musicFeature.domain.model

data class MusicPlaylist(
    val id: String,
    val name: String,
    val trackUris: List<String>,
    val createdAt: Long,
    val updatedAt: Long
) {
    val trackCount: Int get() = trackUris.size
}
