package com.example.musicplayer.core

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.musicplayer.core.theme.MusicPlayerTheme
import com.example.musicplayer.presentation.Navigation
import com.example.musicplayer.presentation.viewModel.AudioViewModel
import com.example.musicplayer.presentation.viewModel.viewModelFactory

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AudioViewModel
    private lateinit var deleteLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == RESULT_OK) viewModel.onDeleteSuccess() else viewModel.onDeleteCancel()
            }

        viewModel = viewModelFactory(applicationContext, this@MainActivity, deleteLauncher)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val allGranted = result.values.all { it }
            if (allGranted) {
                viewModel.loadAudioFiles()
            }
        }

        requestPermissionsIfNeeded(viewModel, this, permissionLauncher)

        setContent {
            MusicPlayerTheme {
                Navigation(viewModel = viewModel)
            }
        }
    }
}