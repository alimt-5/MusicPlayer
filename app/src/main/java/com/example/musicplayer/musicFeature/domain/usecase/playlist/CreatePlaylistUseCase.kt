package com.example.musicplayer.musicFeature.domain.usecase.playlist

import com.example.musicplayer.musicFeature.domain.repository.PlaylistRepository
import javax.inject.Inject

class CreatePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(name: String, trackUris: List<String> = emptyList()): String =
        repository.createPlaylist(name, trackUris)
}
