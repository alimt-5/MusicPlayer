package com.example.musicplayer.musicFeature.presentation.screens.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.presentation.model.HomeUiState
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel

@Composable
fun SongsList(
    state: HomeUiState,
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var trackToDelete by remember { mutableStateOf<AudioTrack?>(null) }
    var trackToRename by remember { mutableStateOf<AudioTrack?>(null) }
    
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.tracks.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.tracks.isEmpty() -> {
                Text("No songs found", modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (state.currentTrack != null) 12.dp else 8.dp)
                ) {
                    items(
                        items = state.tracks,
                        key = { it.id },
                        contentType = { "audio_track" }
                    ) { track ->
                        SongItem(
                            track = track,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = track.id in state.selectedIds,
                            isPlaying = state.currentTrack?.id == track.id && state.isPlaying,
                            onClick = {
                                viewModel.onTrackClicked(track)
                                if (!state.isSelectionMode) onOpenPlayer()
                            },
                            onLongClick = { viewModel.enterSelectionMode(track.id) },
                            onShare = { viewModel.shareTrack(track) },
                            onRename = { trackToRename = track },
                            onDelete = { trackToDelete = track }
                        )
                    }
                }
            }
        }

        if (trackToRename != null) {
            RenameSongDialog(
                track = trackToRename!!,
                onDismiss = { trackToRename = null },
                onConfirm = { newName ->
                    viewModel.requestRename(trackToRename!!, newName)
                    trackToRename = null
                }
            )
        }

        if (trackToDelete != null) {
            DeleteSongDialog(
                track = trackToDelete!!,
                onDismiss = { trackToDelete = null },
                onConfirm = {
                    viewModel.requestDelete(trackToDelete!!)
                    trackToDelete = null
                }
            )
        }
    }
}
