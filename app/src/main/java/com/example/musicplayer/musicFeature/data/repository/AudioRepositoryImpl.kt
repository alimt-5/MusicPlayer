package com.example.musicplayer.musicFeature.data.repository

import com.example.musicplayer.musicFeature.data.local.MediaStoreAudioDataSource
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.repository.AudioRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val dataSource: MediaStoreAudioDataSource
) : AudioRepository {
    override fun observeAudioTracks(): Flow<List<AudioTrack>> = dataSource.observeAudioTracks()
    override suspend fun refreshAudioTracks() = dataSource.refresh()
    override suspend fun deleteTracks(tracks: List<AudioTrack>) = dataSource.deleteTracks(tracks)
    override suspend fun renameTrack(track: AudioTrack, newName: String, afterApproval: Boolean) =
        dataSource.renameTrack(track, newName, afterApproval)
}
