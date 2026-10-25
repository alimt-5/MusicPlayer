package com.example.musicplayer.musicFeature.presentation.screens.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioListScreen(
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAllVisible) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Select all")
                        }
                        IconButton(
                            onClick = viewModel::shareSelected,
                            enabled = state.selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = state.selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (state.currentTrack != null) {
                NowPlayingBottomBar(
                    currentTrack = state.currentTrack!!,
                    isPlaying = state.isPlaying,
                    onPrevious = viewModel::previous,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onOpenPlayer = onOpenPlayer
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (!state.isSelectionMode) {
                SearchBar(state, viewModel)
            }
            SongsList(
                state = state,
                viewModel = viewModel,
                onOpenPlayer = onOpenPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete songs") },
            text = { Text("Delete ${state.selectedIds.size} selected song(s) permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestDeleteSelected()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}
