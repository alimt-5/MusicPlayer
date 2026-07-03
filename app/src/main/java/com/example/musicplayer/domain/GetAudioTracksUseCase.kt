package com.example.musicplayer.domain

class GetAudioTracksUseCase(private val repository: AudioRepository) {
    suspend operator fun invoke(): List<AudioTrack> = repository.getLocalAudioFiles()
}