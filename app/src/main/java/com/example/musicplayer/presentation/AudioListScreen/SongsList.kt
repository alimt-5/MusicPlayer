package com.example.musicplayer.presentation.AudioListScreen

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
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
                    key = { it.id }
                ) { track ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                viewModel.onTrackClick(track)
                                onNavigateToPlayer()
                            },
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val imageModel = remember(track.id) { track.albumArtUri }
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                error = painterResource(R.drawable.app_icon),
                                placeholder = painterResource(R.drawable.app_icon),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            IconButton(onClick = {
                                try {
                                    trackToDelete = track
                                    showDeleteDialog = true
                                } catch (e: Exception) {
                                    Log.e("SongsList", "Error in delete button click", e)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Song")
                            }

                            IconButton(onClick = {
                                viewModel.shareTrack(track)
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share Song",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                        }
                    }
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
                text = { Text("Are you sure you want to delete ${trackToDelete?.title} ?") },
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