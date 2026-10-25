package com.example.musicplayer.musicFeature.domain.repository

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import kotlinx.coroutines.flow.Flow

interface AudioRepository {
    fun observeAudioTracks(): Flow<List<AudioTrack>>
    suspend fun refreshAudioTracks()
    suspend fun deleteTracks(tracks: List<AudioTrack>)
    suspend fun renameTrack(track: AudioTrack, newName: String, afterApproval: Boolean = false)
}
