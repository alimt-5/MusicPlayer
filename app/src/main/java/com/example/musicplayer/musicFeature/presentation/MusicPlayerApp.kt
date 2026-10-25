package com.example.musicplayer.musicFeature.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.musicFeature.presentation.model.MusicEffect
import com.example.musicplayer.musicFeature.presentation.navigation.Navigation
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel

@Composable
fun MusicPlayerApp(viewModel: MusicViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var audioPermissionGranted by rememberSaveable { mutableStateOf(hasAudioPermission(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        audioPermissionGranted = hasAudioPermission(context)
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteConfirmationResult(result.resultCode == Activity.RESULT_OK)
    }

    val renameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onRenameConfirmationResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(audioPermissionGranted) {
        if (audioPermissionGranted) {
            viewModel.refresh()
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MusicEffect.LaunchDeleteConfirmation -> deleteLauncher.launch(effect.request)
                is MusicEffect.LaunchRenameConfirmation -> renameLauncher.launch(effect.request)
                is MusicEffect.ShareAudio -> shareAudio(context, effect.uris)
                MusicEffect.NavigateBackAfterDelete -> if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }

                is MusicEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    if (audioPermissionGranted) {
        Navigation(navController, viewModel)
    } else {
        PermissionScreen {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

private fun hasAudioPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

private fun shareAudio(context: Context, uris: List<String>) {
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uris.first().toUri())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris.map(Uri::parse)))
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share audio"))
}
