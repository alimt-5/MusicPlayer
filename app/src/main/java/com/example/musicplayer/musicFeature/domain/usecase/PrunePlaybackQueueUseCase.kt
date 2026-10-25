package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import javax.inject.Inject

class PrunePlaybackQueueUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    suspend operator fun invoke(availableTrackIds: Set<Long>) {
        repository.pruneQueue(availableTrackIds)
    }
}
