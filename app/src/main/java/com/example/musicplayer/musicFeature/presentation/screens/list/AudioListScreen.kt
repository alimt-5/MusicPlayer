package com.example.musicplayer.musicFeature.presentation.screens.list

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.musicFeature.presentation.viewModels.AudioViewModel

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioListScreen(
    viewModel: AudioViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val selectedCount = state.selectedIds.size
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isSelectionMode) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$selectedCount selected",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.exitSelectionMode() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.selectAll() }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Select All")
                    }
                    if (selectedCount > 0) {
                        IconButton(onClick = { viewModel.shareSelected() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Selected")
                        }
                    }
                    if (selectedCount > 0) {
                        IconButton(onClick = { showDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        } else {
            SearchBar(state, viewModel)
        }

        SongsList(
            state = state,
            listState = listState,
            viewModel = viewModel,
            onNavigateToPlayer = onNavigateToPlayer,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        NowPlayingBottomBar(
            currentTrack = if (state.currentTrack != null) state.currentTrack!! else null,
            isPlaying = state.isPlaying,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onPrevious = { viewModel.previousTrack() },
            onNext = { viewModel.nextTrack() },
            onNavigateToPlayer = onNavigateToPlayer
        )

    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Selected Songs") },
            text = { Text("Are you sure you want to delete $selectedCount selected song${if (selectedCount > 1) "s" else ""}?\n\nThis action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete $selectedCount") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}