package com.example.musicplayer.musicFeature.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Music access is required", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Allow access to audio files so the player can build your local music library.",
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(onClick = onRequestPermission) { Text("Grant access") }
    }
}
