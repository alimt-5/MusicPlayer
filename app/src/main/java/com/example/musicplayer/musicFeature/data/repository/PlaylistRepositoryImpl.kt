package com.example.musicplayer.musicFeature.data.repository

import com.example.musicplayer.musicFeature.data.preferences.PlaylistPreferencesDataSource
import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist
import com.example.musicplayer.musicFeature.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dataSource: PlaylistPreferencesDataSource
) : PlaylistRepository {
    override fun observePlaylists(): Flow<List<MusicPlaylist>> = dataSource.playlists
    override suspend fun createPlaylist(name: String, trackUris: List<String>) = dataSource.createPlaylist(name, trackUris)
    override suspend fun renamePlaylist(id: String, newName: String) = dataSource.renamePlaylist(id, newName)
    override suspend fun addTracks(id: String, trackUris: List<String>) = dataSource.addTracks(id, trackUris)
    override suspend fun removeTrack(id: String, trackUri: String) = dataSource.removeTrack(id, trackUri)
    override suspend fun deletePlaylists(ids: Set<String>) = dataSource.deletePlaylists(ids)
}
