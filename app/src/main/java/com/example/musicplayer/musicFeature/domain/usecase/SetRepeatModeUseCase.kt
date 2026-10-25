package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.RepeatMode
import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import javax.inject.Inject

class SetRepeatModeUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    suspend operator fun invoke(mode: RepeatMode) = repository.setRepeatMode(mode)
}
