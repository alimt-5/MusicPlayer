package com.example.musicplayer.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.musicplayer.presentation.viewModel.AudioViewModel

@ExperimentalMaterial3Api
fun requestPermissionsIfNeeded(
    viewModel: AudioViewModel,
    context: Context,
    activity: MainActivity
) {
    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    if (ContextCompat.checkSelfPermission(
            context,
            permission
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(activity, arrayOf(permission), 100)
    } else {
        viewModel.loadAudioFiles()
    }
}