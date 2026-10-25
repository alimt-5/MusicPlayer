package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.repository.AudioRepository
import javax.inject.Inject

class RefreshAudioTracksUseCase @Inject constructor(
    private val repository: AudioRepository
) {
    suspend operator fun invoke() = repository.refreshAudioTracks()
}
