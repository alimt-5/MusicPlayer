package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLastPlayedTrackIdUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    operator fun invoke(): StateFlow<Long?> = repository.lastPlayedTrackId
}
