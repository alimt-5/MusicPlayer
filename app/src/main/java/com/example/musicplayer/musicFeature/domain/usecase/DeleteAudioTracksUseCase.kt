package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.repository.AudioRepository
import javax.inject.Inject

class DeleteAudioTracksUseCase @Inject constructor(
    private val repository: AudioRepository
) {
    suspend operator fun invoke(tracks: List<AudioTrack>) = repository.deleteTracks(tracks)
}
