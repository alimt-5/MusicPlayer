package com.example.musicplayer.musicFeature.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(
    needsLegacyWritePermission: Boolean = false,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Music access is required", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (needsLegacyWritePermission) {
                "Allow audio and storage access. Android 9 also requires storage write permission to rename and delete music files."
            } else {
                "Allow access to audio files so the player can build your local music library."
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(onClick = onRequestPermission) { Text("Grant access") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}
