package com.example.musicplayer.presentation.AudioListScreen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicplayer.domain.AudioTrack
import com.example.musicplayer.presentation.HomeUiState
import com.example.musicplayer.presentation.viewModel.AudioViewModel

@RequiresApi(Build.VERSION_CODES.R)
@ExperimentalMaterial3Api
@Composable
fun SongsList(
    state: HomeUiState,
    listState: LazyListState,
    viewModel: AudioViewModel,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<AudioTrack?>(null) }
    val bottomPadding = if (state.currentTrack != null) 70.dp else 8.dp

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (state.tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Songs Not Found!")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(
                    items = state.tracks,
                    key = { track -> track.id },
                    contentType = { "audio_track" }
                ) { track ->
                    SongItem(
                        track = track,
                        isSelectionMode = state.isSelectionMode,
                        isSelected = track.id in state.selectedIds,
                        onClick = {
                            if (state.isSelectionMode) {
                                viewModel.toggleSelection(track.id)
                            } else {
                                viewModel.onTrackClick(track)
                                onNavigateToPlayer()
                            }
                        },
                        onLongClick = {
                            if (!state.isSelectionMode) {
                                viewModel.enterSelectionMode(track.id)
                            }
                        },
                        onShare = {
                            viewModel.shareTrack(track)
                        },
                        onDelete = {
                            trackToDelete = track
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }

        if (showDeleteDialog && trackToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    trackToDelete = null
                },
                title = { Text("Delete Song") },
                text = { Text("Are you sure you want to delete\n${trackToDelete?.title} -> ${trackToDelete?.artist} ?") },
                confirmButton = {
                    Button(
                        onClick = {
                            trackToDelete?.let { viewModel.deleteTrack(it) }
                            showDeleteDialog = false
                            trackToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        trackToDelete = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}