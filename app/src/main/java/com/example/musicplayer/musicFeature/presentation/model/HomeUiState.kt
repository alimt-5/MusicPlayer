package com.example.musicplayer.musicFeature.presentation.model

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.RepeatMode
import com.example.musicplayer.musicFeature.domain.model.SortType

data class HomeUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val searchQuery: String = "",
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.REPEAT_ALL,
    val sortType: SortType = SortType.ALPHABETICAL,
    val isLoading: Boolean = true,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
)
