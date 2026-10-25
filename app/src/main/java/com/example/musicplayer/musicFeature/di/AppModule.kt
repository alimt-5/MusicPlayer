package com.example.musicplayer.musicFeature.di

import com.example.musicplayer.musicFeature.data.playback.Media3PlaybackRepository
import com.example.musicplayer.musicFeature.data.repository.AudioRepositoryImpl
import com.example.musicplayer.musicFeature.data.repository.SortPreferencesRepositoryImpl
import com.example.musicplayer.musicFeature.domain.repository.AudioRepository
import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import com.example.musicplayer.musicFeature.domain.repository.SortPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindAudioRepository(impl: AudioRepositoryImpl): AudioRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(impl: Media3PlaybackRepository): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindSortPreferencesRepository(impl: SortPreferencesRepositoryImpl): SortPreferencesRepository
}
