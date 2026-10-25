package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import javax.inject.Inject

class PlayTracksUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    suspend operator fun invoke(playlist: List<AudioTrack>, index: Int) = repository.play(playlist, index)
}
