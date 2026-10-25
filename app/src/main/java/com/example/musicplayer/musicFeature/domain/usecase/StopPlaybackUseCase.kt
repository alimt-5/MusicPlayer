package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import javax.inject.Inject

class StopPlaybackUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    suspend operator fun invoke() = repository.stop()
}
