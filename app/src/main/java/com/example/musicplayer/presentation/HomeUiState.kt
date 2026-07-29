package com.example.musicplayer.presentation

import com.example.musicplayer.domain.AudioTrack

data class HomeUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val searchQuery: String = "",
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val sortType: SortType = SortType.ALPHABETICAL,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL
)