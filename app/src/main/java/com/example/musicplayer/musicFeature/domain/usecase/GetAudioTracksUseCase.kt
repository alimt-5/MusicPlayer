package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.repository.AudioRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAudioTracksUseCase @Inject constructor(
    private val repository: AudioRepository
) {
    operator fun invoke(): Flow<List<AudioTrack>> = repository.observeAudioTracks()
}
