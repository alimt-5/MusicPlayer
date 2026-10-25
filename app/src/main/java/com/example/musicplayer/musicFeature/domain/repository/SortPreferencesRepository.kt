package com.example.musicplayer.musicFeature.domain.repository

import com.example.musicplayer.musicFeature.domain.model.SortType
import kotlinx.coroutines.flow.Flow

interface SortPreferencesRepository {
    val sortType: Flow<SortType>
    suspend fun setSortType(sortType: SortType)
}
