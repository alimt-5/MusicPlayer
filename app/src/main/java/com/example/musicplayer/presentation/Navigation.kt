package com.example.musicplayer.presentation

import android.annotation.SuppressLint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.presentation.AudioListScreen.AudioListScreen
import com.example.musicplayer.presentation.viewModel.AudioViewModel

@SuppressLint("UnsafeOptInUsageError")
@ExperimentalMaterial3Api
@Composable
fun Navigation(viewModel: AudioViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            AudioListScreen(viewModel) { navController.navigate("player") }
        }
        composable("player") {
            NowPlayingScreen(viewModel) { navController.popBackStack() }
        }
    }
}