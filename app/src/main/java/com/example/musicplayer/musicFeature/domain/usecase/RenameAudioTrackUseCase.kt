package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.repository.AudioRepository
import javax.inject.Inject

class RenameAudioTrackUseCase @Inject constructor(
    private val repository: AudioRepository
) {
    suspend operator fun invoke(track: AudioTrack, newName: String, afterApproval: Boolean = false) =
        repository.renameTrack(track, newName, afterApproval)
}
