package com.example.musicplayer.musicFeature.presentation.model

import androidx.activity.result.IntentSenderRequest

sealed interface MusicEffect {
    data class LaunchDeleteConfirmation(val request: IntentSenderRequest) : MusicEffect
    data class LaunchRenameConfirmation(val request: IntentSenderRequest) : MusicEffect
    data class ShareAudio(val uris: List<String>) : MusicEffect
    data object NavigateBackAfterDelete : MusicEffect
    data class ShowMessage(val message: String) : MusicEffect
}
