package com.example.musicplayer.musicFeature.domain.usecase.playlist

import com.example.musicplayer.musicFeature.domain.repository.PlaylistRepository
import javax.inject.Inject

class RenamePlaylistUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    suspend operator fun invoke(id: String, name: String) = repository.renamePlaylist(id, name)
}
