package com.example.musicplayer.musicFeature.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.example.musicplayer.musicFeature.presentation.MusicPlayerApp
import com.example.musicplayer.musicFeature.presentation.theme.MusicPlayerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicPlayerTheme {
                MusicPlayerApp()
            }
        }
    }
}
