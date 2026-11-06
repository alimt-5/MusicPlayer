package com.example.musicplayer.musicFeature.presentation.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.presentation.screens.list.SongAlbumArt
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel
import com.example.musicplayer.musicFeature.presentation.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTracksToPlaylistScreen(
    playlistId: String,
    playlistViewModel: PlaylistViewModel,
    musicViewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val state by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val libraryTracks by musicViewModel.libraryTracks.collectAsStateWithLifecycle()
    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val playlist = state.playlists.firstOrNull { it.id == playlistId }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedUris by remember(playlistId) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(playlist?.trackUris) {
        selectedUris = playlist?.trackUris?.toSet().orEmpty()
    }

    val filteredTracks = remember(libraryTracks, search) {
        if (search.isBlank()) libraryTracks
        else libraryTracks.filter { track ->
            track.title.contains(search, ignoreCase = true) || track.artist.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add songs${playlist?.let { " · ${it.name}" }.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = onBack) { Text("Done") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search songs or artists") }
            )
            Text(
                "${selectedUris.size} songs selected",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            when {
                state.isLoading && playlist == null -> {
                    BoxLoading()
                }
                playlist == null -> {
                    Text("Playlist not found", modifier = Modifier.padding(24.dp))
                }
                musicState.isLoading && libraryTracks.isEmpty() -> {
                    BoxLoading()
                }
                filteredTracks.isEmpty() -> {
                    Text(
                        if (search.isBlank()) "No songs found" else "No matching songs",
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredTracks, key = AudioTrack::mediaUri) { track ->
                            val checked = track.mediaUri in selectedUris
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedUris = if (checked) selectedUris - track.mediaUri else selectedUris + track.mediaUri
                                    playlistViewModel.updateTrackMembership(playlistId, track.mediaUri, !checked)
                                }.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongAlbumArt(track.albumArtUri)
                                Spacer(Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { enabled ->
                                        selectedUris = if (enabled) selectedUris + track.mediaUri else selectedUris - track.mediaUri
                                        playlistViewModel.updateTrackMembership(playlistId, track.mediaUri, enabled)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxLoading() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}
