package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.PlaybackState
import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObservePlaybackStateUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    operator fun invoke(): StateFlow<PlaybackState> = repository.state
}
