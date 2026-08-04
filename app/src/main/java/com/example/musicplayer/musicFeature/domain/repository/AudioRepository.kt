package com.example.musicplayer.musicFeature.domain.repository

import com.example.musicplayer.musicFeature.domain.model.AudioTrack

interface AudioRepository {
    suspend fun getLocalAudioFiles(): List<AudioTrack>
}