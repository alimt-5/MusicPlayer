package com.example.musicplayer.musicFeature.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist
import com.example.musicplayer.musicFeature.domain.usecase.playlist.CreatePlaylistUseCase
import com.example.musicplayer.musicFeature.domain.usecase.playlist.DeletePlaylistsUseCase
import com.example.musicplayer.musicFeature.domain.usecase.playlist.ModifyPlaylistTracksUseCase
import com.example.musicplayer.musicFeature.domain.usecase.playlist.ObservePlaylistsUseCase
import com.example.musicplayer.musicFeature.domain.usecase.playlist.RenamePlaylistUseCase
import com.example.musicplayer.musicFeature.presentation.model.PlaylistUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val renamePlaylistUseCase: RenamePlaylistUseCase,
    private val modifyPlaylistTracks: ModifyPlaylistTracksUseCase,
    private val deletePlaylistsUseCase: DeletePlaylistsUseCase
) : ViewModel() {
    private val internalMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val playlistsLoading = MutableStateFlow(true)
    val messages = internalMessages.asSharedFlow()

    private val playlists: StateFlow<List<MusicPlaylist>> = observePlaylistsUseCase()
        .onEach { playlistsLoading.value = false }
        .catch { error ->
            playlistsLoading.value = false
            Log.e("MusicPlaylists", "Could not observe playlists", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<PlaylistUiState> = combine(playlists, playlistsLoading) { items, loading ->
        PlaylistUiState(playlists = items, isLoading = loading)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistUiState())

    fun createPlaylist(name: String, tracks: List<AudioTrack> = emptyList()) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            internalMessages.tryEmit("Playlist name cannot be empty")
            return
        }
        viewModelScope.launch {
            runCatching { createPlaylistUseCase(clean, tracks.map(AudioTrack::mediaUri).distinct()) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e("MusicPlaylists", "Could not create playlist", error)
                    internalMessages.emit(error.message ?: "Could not create playlist")
                }
        }
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            internalMessages.tryEmit("Playlist name cannot be empty")
            return
        }
        viewModelScope.launch {
            runCatching { renamePlaylistUseCase(id, clean) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e("MusicPlaylists", "Could not rename playlist", error)
                    internalMessages.emit(error.message ?: "Could not rename playlist")
                }
        }
    }

    fun addTracksToPlaylist(id: String, tracks: List<AudioTrack>) {
        val uris = tracks.map(AudioTrack::mediaUri).distinct()
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runCatching { modifyPlaylistTracks.add(id, uris) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e("MusicPlaylists", "Could not add tracks to playlist", error)
                    internalMessages.emit(error.message ?: "Could not add tracks to playlist")
                }
        }
    }

    fun updateTrackMembership(id: String, trackUri: String, included: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (included) modifyPlaylistTracks.add(id, listOf(trackUri))
                else modifyPlaylistTracks.remove(id, trackUri)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.e("MusicPlaylists", "Could not update playlist tracks", error)
                internalMessages.emit(error.message ?: "Could not update playlist")
            }
        }
    }

    fun removeTrackFromPlaylist(id: String, trackUri: String) {
        updateTrackMembership(id, trackUri, included = false)
    }

    fun deletePlaylists(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { deletePlaylistsUseCase(ids) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e("MusicPlaylists", "Could not delete playlists", error)
                    internalMessages.emit(error.message ?: "Could not delete playlists")
                }
        }
    }

    fun getPlaylist(id: String): MusicPlaylist? = playlists.value.firstOrNull { it.id == id }

}
