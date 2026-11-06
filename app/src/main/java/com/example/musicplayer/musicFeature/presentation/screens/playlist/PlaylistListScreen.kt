package com.example.musicplayer.musicFeature.presentation.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel
import com.example.musicplayer.musicFeature.presentation.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    playlistViewModel: PlaylistViewModel,
    musicViewModel: MusicViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    val state by playlistViewModel.uiState.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<MusicPlaylist?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedIds.size} selected" else "Playlists") },
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) selectedIds = emptySet() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (selectionMode) "Clear selection" else "Back")
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { selectedIds = state.playlists.mapTo(LinkedHashSet<String>()) { it.id } }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = {
                            musicViewModel.shareTracks(musicViewModel.tracksForMediaUris(state.playlists.filter { it.id in selectedIds }.flatMap(MusicPlaylist::trackUris)))
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share selected playlists")
                        }
                        IconButton(onClick = {
                            pendingDeleteIds = selectedIds
                            showDeleteDialog = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected playlists", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Create playlist")
                }
            }
        }
    ) { padding ->
        if (state.isLoading && state.playlists.isEmpty()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (state.playlists.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No playlists yet", style = MaterialTheme.typography.titleLarge)
                Text("Create a playlist to organize your music.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.playlists, key = MusicPlaylist::id) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        isSelectionMode = selectionMode,
                        isSelected = playlist.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (playlist.id in selectedIds) selectedIds - playlist.id else selectedIds + playlist.id
                            } else onOpenPlaylist(playlist.id)
                        },
                        onLongClick = {
                            selectedIds = selectedIds + playlist.id
                        },
                        onRename = { playlistToRename = playlist },
                        onShare = { musicViewModel.shareTracks(musicViewModel.tracksForMediaUris(playlist.trackUris)) },
                        onDelete = {
                            pendingDeleteIds = setOf(playlist.id)
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "Create playlist",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name -> playlistViewModel.createPlaylist(name); showCreateDialog = false }
        )
    }

    playlistToRename?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = playlist.name,
            onDismiss = { playlistToRename = null },
            onConfirm = { name -> playlistViewModel.renamePlaylist(playlist.id, name); playlistToRename = null }
        )
    }

    if (showDeleteDialog) {
        val targets = state.playlists.filter { it.id in pendingDeleteIds }
        DeletePlaylistsDialog(
            playlistNames = targets.map(MusicPlaylist::name),
            onDismiss = { showDeleteDialog = false; pendingDeleteIds = emptySet() },
            onConfirm = { alsoDeleteAudioFiles ->
                if (alsoDeleteAudioFiles) {
                    musicViewModel.deleteTracksFromPlaylist(musicViewModel.tracksForMediaUris(targets.flatMap(MusicPlaylist::trackUris)))
                }
                playlistViewModel.deletePlaylists(pendingDeleteIds)
                selectedIds = emptySet()
                pendingDeleteIds = emptySet()
                showDeleteDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: MusicPlaylist,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(playlist.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(12.dp))
            }
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${playlist.trackCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isSelectionMode) {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Playlist options") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

