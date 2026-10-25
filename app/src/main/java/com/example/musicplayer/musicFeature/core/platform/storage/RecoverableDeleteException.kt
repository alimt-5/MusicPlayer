package com.example.musicplayer.musicFeature.core.platform.storage

import androidx.activity.result.IntentSenderRequest

class RecoverableDeleteException(
    val request: IntentSenderRequest,
    val retryAfterApproval: Boolean,
    val completedTrackIds: Set<Long> = emptySet()
) : Exception("User approval is required to delete media")
