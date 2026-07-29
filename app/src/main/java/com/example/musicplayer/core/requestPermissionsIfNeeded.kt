package com.example.musicplayer.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val missingPermissions = permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    if (missingPermissions.isNotEmpty()) {
        ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), 100)
    } else {
        viewModel.loadAudioFiles()
    }
}
