package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.SortType
import com.example.musicplayer.musicFeature.domain.repository.SortPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSortTypeUseCase @Inject constructor(
    private val repository: SortPreferencesRepository
) {
    operator fun invoke(): Flow<SortType> = repository.sortType
}
