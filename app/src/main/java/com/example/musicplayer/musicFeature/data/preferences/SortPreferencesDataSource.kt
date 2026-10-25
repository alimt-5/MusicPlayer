package com.example.musicplayer.musicFeature.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.musicplayer.musicFeature.domain.model.SortType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "music_settings")

@Singleton
class SortPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sortKey = stringPreferencesKey("sort_type")

    val sortType: Flow<SortType> = context.settingsDataStore.data.map { preferences ->
        when (preferences[sortKey]) {
            SortType.DATE_ADDED.name -> SortType.DATE_ADDED
            else -> SortType.ALPHABETICAL
        }
    }

    suspend fun setSortType(sortType: SortType) {
        context.settingsDataStore.edit { preferences ->
            preferences[sortKey] = sortType.name
        }
    }
}
