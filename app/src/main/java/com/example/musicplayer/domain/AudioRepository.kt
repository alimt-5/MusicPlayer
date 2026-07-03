package com.example.musicplayer.domain

interface AudioRepository {
    suspend fun getLocalAudioFiles(): List<AudioTrack>
}