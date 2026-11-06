package com.example.musicplayer.musicFeature.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.musicplayer.musicFeature.presentation.screens.list.AudioListScreen
import com.example.musicplayer.musicFeature.presentation.screens.player.NowPlayingScreen
import com.example.musicplayer.musicFeature.presentation.screens.playlist.AddTracksToPlaylistScreen
import com.example.musicplayer.musicFeature.presentation.screens.playlist.PlaylistDetailScreen
import com.example.musicplayer.musicFeature.presentation.screens.playlist.PlaylistListScreen
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel
import com.example.musicplayer.musicFeature.presentation.viewmodel.PlaylistViewModel

private const val LIST = "list"
private const val PLAYER = "player"
private const val PLAYLISTS = "playlists"
private const val PLAYLIST_DETAIL = "playlist/{playlistId}"
private const val PLAYLIST_ADD_TRACKS = "playlist/{playlistId}/add"

@Composable
fun Navigation(
    navController: NavHostController,
    musicViewModel: MusicViewModel,
    playlistViewModel: PlaylistViewModel
) {
    NavHost(navController = navController, startDestination = LIST) {
        composable(LIST) {
            AudioListScreen(
                viewModel = musicViewModel,
                playlistViewModel = playlistViewModel,
                onOpenPlayer = { navController.navigate(PLAYER) },
                onOpenPlaylists = { navController.navigate(PLAYLISTS) }
            )
        }
        composable(PLAYER) {
            NowPlayingScreen(
                viewModel = musicViewModel,
                playlistViewModel = playlistViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(PLAYLISTS) {
            PlaylistListScreen(
                playlistViewModel = playlistViewModel,
                musicViewModel = musicViewModel,
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { id -> navController.navigate("playlist/$id") }
            )
        }
        composable(
            route = PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("playlistId").orEmpty()
            PlaylistDetailScreen(
                playlistId = id,
                playlistViewModel = playlistViewModel,
                musicViewModel = musicViewModel,
                onBack = { navController.popBackStack() },
                onAddTracks = { navController.navigate("playlist/$id/add") }
            )
        }
        composable(
            route = PLAYLIST_ADD_TRACKS,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("playlistId").orEmpty()
            AddTracksToPlaylistScreen(
                playlistId = id,
                playlistViewModel = playlistViewModel,
                musicViewModel = musicViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
