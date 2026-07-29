package com.example.musicplayer.presentation.AudioListScreen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.musicplayer.presentation.viewModel.AudioViewModel

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioListScreen(
    viewModel: AudioViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchBar(state, viewModel)

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
}