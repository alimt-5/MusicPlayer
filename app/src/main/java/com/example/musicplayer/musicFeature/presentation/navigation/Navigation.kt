package com.example.musicplayer.musicFeature.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.musicplayer.musicFeature.presentation.screens.list.AudioListScreen
import com.example.musicplayer.musicFeature.presentation.screens.player.NowPlayingScreen
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel

private const val LIST = "list"
private const val PLAYER = "player"

@Composable
fun Navigation(
    navController: NavHostController,
    viewModel: MusicViewModel
) {
    NavHost(navController = navController, startDestination = LIST) {
        composable(LIST) {
            AudioListScreen(
                viewModel = viewModel,
                onOpenPlayer = { navController.navigate(PLAYER) }
            )
        }
        composable(PLAYER) {
            NowPlayingScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
