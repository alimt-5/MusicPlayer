package com.example.musicplayer.musicFeature.domain.usecase.playlist

import com.example.musicplayer.musicFeature.domain.repository.PlaylistRepository
import javax.inject.Inject

class DeletePlaylistsUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(ids: Set<String>) = repository.deletePlaylists(ids)
}
