package com.example.musicplayer.core

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import com.example.musicplayer.core.theme.MusicPlayerTheme
import com.example.musicplayer.presentation.Navigation
import com.example.musicplayer.presentation.viewModel.AudioViewModel
import com.example.musicplayer.presentation.viewModel.viewModelFactory

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)

    private lateinit var viewModel: AudioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = viewModelFactory(applicationContext, this@MainActivity)

        setContent {
            MusicPlayerTheme {
                Navigation(viewModel = viewModel)
            }
        }
        requestPermissionsIfNeeded(viewModel, this, this)
    }


    @Suppress("UNCHECKED_CAST")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions as Array<String>, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadAudioFiles()
        }
    }
}