package com.example.musicplayer.musicFeature.presentation.screens.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.musicFeature.domain.model.AudioTrack

@Composable
fun NowPlayingBottomBar(
    currentTrack: AudioTrack,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlayer), shape = RoundedCornerShape(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = currentTrack.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.res.painterResource(R.drawable.app_icon),
                placeholder = androidx.compose.ui.res.painterResource(R.drawable.app_icon)
            )
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(currentTrack.title, maxLines = 1)
                    Text(currentTrack.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            IconButton(onClick = onTogglePlayPause) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        }
    }
}
