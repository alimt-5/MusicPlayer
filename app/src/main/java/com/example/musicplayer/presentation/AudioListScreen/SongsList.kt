package com.example.musicplayer.presentation.AudioListScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.presentation.HomeUiState
import com.example.musicplayer.presentation.viewModel.AudioViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@ExperimentalMaterial3Api
@Composable
fun SongsList(
    state: HomeUiState,
    listState: LazyListState,
    viewModel: AudioViewModel,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val scrollProgress by remember {
        derivedStateOf {
            try {
                val layout = listState.layoutInfo
                val total = layout.totalItemsCount
                if (total == 0) {
                    0f
                } else {
                    val first = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    val itemHeight = layout.visibleItemsInfo.firstOrNull()?.size ?: 100
                    ((first + offset.toFloat() / itemHeight) /
                            (total - 1).coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                }
            } catch (e: Exception) {
                0f
            }
        }
    }

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
                        }
                    }
                }
            }

            if (state.tracks.size > 1) {
                ScrollBar(
                    progress = scrollProgress,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                    onDrag = { progress ->
                        scrollJob?.cancel()
                        scrollJob = scope.launch {
                            try {
                                val total = state.tracks.size
                                if (total == 0) return@launch

                                val targetIndex = (progress * (total - 1)).toInt()
                                    .coerceIn(0, total - 1)

                                listState.scrollToItem(targetIndex, 0)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                )
            }
        }
    }
}