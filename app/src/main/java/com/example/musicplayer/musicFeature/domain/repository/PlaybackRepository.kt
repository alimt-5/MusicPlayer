package com.example.musicplayer.musicFeature.domain.repository

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.PlaybackPosition
import com.example.musicplayer.musicFeature.domain.model.PlaybackState
import com.example.musicplayer.musicFeature.domain.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackRepository {
    val state: StateFlow<PlaybackState>
    val lastPlayedTrackId: StateFlow<Long?>
    fun position(): Flow<PlaybackPosition>
    suspend fun connect()
    suspend fun play(playlist: List<AudioTrack>, index: Int)
    suspend fun togglePlayPause()
    suspend fun next()
    suspend fun previous()
    suspend fun seekTo(progress: Float)
    suspend fun setRepeatMode(mode: RepeatMode)
    suspend fun pruneQueue(availableTrackIds: Set<Long>)
    suspend fun stop()
}
