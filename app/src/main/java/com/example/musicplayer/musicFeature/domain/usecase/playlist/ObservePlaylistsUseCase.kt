package com.example.musicplayer.musicFeature.domain.usecase.playlist

import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist
import com.example.musicplayer.musicFeature.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePlaylistsUseCase @Inject constructor(
    private val repository: PlaylistRepository
) {
    operator fun invoke(): Flow<List<MusicPlaylist>> = repository.observePlaylists()
}
