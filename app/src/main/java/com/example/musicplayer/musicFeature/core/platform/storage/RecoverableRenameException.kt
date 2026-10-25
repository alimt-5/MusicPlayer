package com.example.musicplayer.musicFeature.core.platform.storage

import androidx.activity.result.IntentSenderRequest

class RecoverableRenameException(
    val request: IntentSenderRequest
) : Exception("User approval is required to rename this media item")
