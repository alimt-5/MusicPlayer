package com.example.musicplayer.musicFeature.presentation.model

import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist

data class PlaylistUiState(
    val playlists: List<MusicPlaylist> = emptyList(),
    val isLoading: Boolean = true
)
