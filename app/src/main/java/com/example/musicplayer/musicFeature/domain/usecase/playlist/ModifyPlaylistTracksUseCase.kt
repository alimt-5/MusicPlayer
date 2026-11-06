package com.example.musicplayer.musicFeature.domain.usecase.playlist

import com.example.musicplayer.musicFeature.domain.repository.PlaylistRepository
import javax.inject.Inject

class ModifyPlaylistTracksUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend fun add(id: String, trackUris: List<String>) = repository.addTracks(id, trackUris)
    suspend fun remove(id: String, trackUri: String) = repository.removeTrack(id, trackUri)
}
