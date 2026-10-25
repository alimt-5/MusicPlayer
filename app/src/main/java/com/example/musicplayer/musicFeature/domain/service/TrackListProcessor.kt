package com.example.musicplayer.musicFeature.domain.service

import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.SortType
import java.util.Locale

object TrackListProcessor {
    fun sort(tracks: List<AudioTrack>, sortType: SortType): List<AudioTrack> = when (sortType) {
        SortType.ALPHABETICAL -> tracks.sortedWith(
            compareBy<AudioTrack> { it.normalizedTitle }
                .thenBy { it.searchText }
                .thenBy { it.id }
        )
        SortType.DATE_ADDED -> tracks.sortedWith(
            compareByDescending<AudioTrack> { it.dateAddedMs }
                .thenBy { it.normalizedTitle }
                .thenBy { it.id }
        )
    }

    fun filter(sortedTracks: List<AudioTrack>, query: String): List<AudioTrack> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return sortedTracks
        return sortedTracks.filter { normalizedQuery in it.searchText }
    }
}
