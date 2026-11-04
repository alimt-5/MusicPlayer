package com.example.musicplayer.musicFeature.core.platform.storage

class PartialDeleteException(
    val completedTrackIds: Set<Long>,
    val failedTrackTitles: List<String>
) : Exception("Some selected songs could not be deleted")
