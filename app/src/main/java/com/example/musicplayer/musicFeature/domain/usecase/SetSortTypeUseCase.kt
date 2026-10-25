package com.example.musicplayer.musicFeature.domain.usecase

import com.example.musicplayer.musicFeature.domain.model.SortType
import com.example.musicplayer.musicFeature.domain.repository.SortPreferencesRepository
import javax.inject.Inject

class SetSortTypeUseCase @Inject constructor(
    private val repository: SortPreferencesRepository
) {
    suspend operator fun invoke(sortType: SortType) = repository.setSortType(sortType)
}
