package com.example.musicplayer.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.presentation.viewModel.AudioViewModel
import java.util.concurrent.TimeUnit

@ExperimentalMaterial3Api
@androidx.media3.common.util.UnstableApi
@Composable
fun NowPlayingScreen(viewModel: AudioViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(playbackProgress) }

    val sliderValue = if (isDragging) dragProgress else playbackProgress

    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isSliderPressed by sliderInteractionSource.collectIsPressedAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (isSliderPressed) 1.5f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "thumb_scale"
    )

    val currentTrack = state.currentTrack
    val durationMs = currentTrack?.duration ?: 0L

    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = currentTrack?.albumArtUri,
            contentDescription = "Album Art",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            error = painterResource(R.drawable.app_icon),
            placeholder = painterResource(R.drawable.app_icon),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = currentTrack?.title ?: "Unknown",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = currentTrack?.artist ?: "Unknown",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column {
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    isDragging = true
                    dragProgress = newValue
                    viewModel.seekTo(newValue)
                },
                onValueChangeFinished = {
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                interactionSource = sliderInteractionSource,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = sliderInteractionSource,
                        colors = SliderDefaults.colors(),
                        modifier = Modifier.scale(thumbScale)
                    )
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val elapsedMs = (durationMs * sliderValue).toLong()
                Text(
                    text = formatTime(elapsedMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                val icon = when (state.repeatMode) {
                    RepeatMode.OFF -> Icons.Default.Repeat
                    RepeatMode.REPEAT_ALL -> Icons.Default.Repeat
                    RepeatMode.REPEAT_ONE -> Icons.Default.RepeatOne
                }
                val tint = when (state.repeatMode) {
                    RepeatMode.OFF -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.primary
                }
                Icon(icon, contentDescription = "Repeat", tint = tint)
            }

            IconButton(onClick = { viewModel.previousTrack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "previous", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { viewModel.nextTrack() }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "next", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}