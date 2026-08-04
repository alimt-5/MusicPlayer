package com.example.musicplayer.musicFeature.presentation.navigation

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.musicFeature.presentation.screens.list.AudioListScreen
import com.example.musicplayer.musicFeature.presentation.screens.palyer.NowPlayingScreen
import com.example.musicplayer.musicFeature.presentation.viewModels.AudioViewModel

@RequiresApi(Build.VERSION_CODES.R)
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