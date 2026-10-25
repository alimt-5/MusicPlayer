package com.example.musicplayer.musicFeature.data.repository

import com.example.musicplayer.musicFeature.data.preferences.SortPreferencesDataSource
import com.example.musicplayer.musicFeature.domain.model.SortType
import com.example.musicplayer.musicFeature.domain.repository.SortPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SortPreferencesRepositoryImpl @Inject constructor(
    private val dataSource: SortPreferencesDataSource
) : SortPreferencesRepository {
    override val sortType: Flow<SortType> = dataSource.sortType
    override suspend fun setSortType(sortType: SortType) = dataSource.setSortType(sortType)
}
