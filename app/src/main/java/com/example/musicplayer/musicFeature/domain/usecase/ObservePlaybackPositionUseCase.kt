package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.PlaybackPosition
import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePlaybackPositionUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    operator fun invoke(): Flow<PlaybackPosition> = repository.position()
}
