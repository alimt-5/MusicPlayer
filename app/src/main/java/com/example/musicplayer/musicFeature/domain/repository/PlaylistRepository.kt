package com.example.musicplayer.musicFeature.domain.repository

import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<MusicPlaylist>>
    suspend fun createPlaylist(name: String, trackUris: List<String> = emptyList()): String
    suspend fun renamePlaylist(id: String, newName: String)
    suspend fun addTracks(id: String, trackUris: List<String>)
    suspend fun removeTrack(id: String, trackUri: String)
    suspend fun deletePlaylists(ids: Set<String>)
}
