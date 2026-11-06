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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist
import com.example.musicplayer.musicFeature.presentation.screens.list.SongAlbumArt
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel
import com.example.musicplayer.musicFeature.presentation.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playlistViewModel: PlaylistViewModel,
    musicViewModel: MusicViewModel,
    onBack: () -> Unit,
    onAddTracks: () -> Unit
) {
    val state by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val libraryTracks by musicViewModel.libraryTracks.collectAsStateWithLifecycle()
    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val playlist = state.playlists.firstOrNull { it.id == playlistId }
    val tracks = remember(playlist?.trackUris, libraryTracks) {
        val byUri = libraryTracks.associateBy(AudioTrack::mediaUri)
        playlist?.trackUris.orEmpty().mapNotNull(byUri::get)
    }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var deleteAudioFiles by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { musicViewModel.shareTracks(tracks) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share playlist")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Playlist options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; showRename = true })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; showDelete = true })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTracks) {
                Icon(Icons.Default.Add, contentDescription = "Add songs")
            }
        }
    ) { padding ->
        if (playlist == null && state.isLoading) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { androidx.compose.material3.CircularProgressIndicator() }
        } else if (playlist == null) {
            Text("Playlist not found", modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp))
        } else if (tracks.isEmpty() && musicState.isLoading) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { androidx.compose.material3.CircularProgressIndicator() }
        } else if (tracks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (playlist.trackUris.isEmpty()) "This playlist is empty" else "No available songs in this playlist",
                    style = MaterialTheme.typography.titleLarge
                )
                Text("Use the + button to add songs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        "${tracks.size} available songs",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                itemsIndexed(tracks, key = { _, track -> track.mediaUri }) { index, track ->
                    PlaylistTrackRow(
                        track = track,
                        onClick = { musicViewModel.playTrackCollection(tracks, index) },
                        onRemove = { playlistViewModel.removeTrackFromPlaylist(playlistId, track.mediaUri) }
                    )
                }
            }
        }
    }

    if (showRename && playlist != null) {
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { playlistViewModel.renamePlaylist(playlistId, it); showRename = false }
        )
    }

    if (showDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete '${playlist.name}'?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = deleteAudioFiles, onCheckedChange = { deleteAudioFiles = it })
                        Text("Also delete audio files from device")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteAudioFiles) musicViewModel.deleteTracksFromPlaylist(tracks)
                    playlistViewModel.deletePlaylists(setOf(playlistId))
                    showDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PlaylistTrackRow(track: AudioTrack, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongAlbumArt(track.albumArtUri)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClick) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove from playlist", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
