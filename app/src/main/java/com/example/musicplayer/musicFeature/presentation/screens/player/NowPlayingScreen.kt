package com.example.musicplayer.musicFeature.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.PlaybackPosition
import com.example.musicplayer.musicFeature.domain.model.RepeatMode
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel
import com.example.musicplayer.musicFeature.presentation.screens.list.RenameSongDialog
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position by viewModel.playbackPosition.collectAsStateWithLifecycle(initialValue = PlaybackPosition())
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    val currentTrack = state.currentTrack
    val duration = position.durationMs.takeIf { it > 0 } ?: currentTrack?.durationMs ?: 0L
    val playerProgress = if (duration > 0) (position.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val sliderValue = if (isDragging) dragProgress else playerProgress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTrack?.title ?: "Now Playing", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { currentTrack?.let(viewModel::shareTrack); menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; showRenameDialog = currentTrack != null }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; showDeleteDialog = currentTrack != null }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = currentTrack?.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(260.dp).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.app_icon),
                placeholder = painterResource(R.drawable.app_icon)
            )
            Spacer(Modifier.height(24.dp))
            Text(currentTrack?.title ?: "Unknown", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(currentTrack?.artist ?: "Unknown", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(26.dp))
            Slider(
                value = sliderValue,
                onValueChange = {
                    isDragging = true
                    dragProgress = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    viewModel.seekTo(dragProgress)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((duration * sliderValue).toLong()), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::toggleRepeatMode) {
                    val icon = if (state.repeatMode == RepeatMode.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    val tint = if (state.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    Icon(icon, null, tint = tint)
                }
                IconButton(onClick = viewModel::previous) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous") }
                IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
                IconButton(onClick = viewModel::next) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next") }
            }
        }
    }

    if (showRenameDialog && currentTrack != null) {
        RenameSongDialog(
            track = currentTrack,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.requestRename(currentTrack, newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog && currentTrack != null) {
        DeletePlayerSongDialog(
            track = currentTrack,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.requestDelete(currentTrack)
                showDeleteDialog = false
            }
        )
    }
}

@Composable
private fun DeletePlayerSongDialog(track: AudioTrack, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete song") },
        text = { Text("Delete ${track.title} permanently?") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(safe)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
