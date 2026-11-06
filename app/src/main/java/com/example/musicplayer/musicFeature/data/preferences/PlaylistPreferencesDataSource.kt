package com.example.musicplayer.musicFeature.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.musicplayer.musicFeature.domain.model.MusicPlaylist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playlistsDataStore by preferencesDataStore(name = "music_playlists")

@Singleton
class PlaylistPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val playlistsKey = stringPreferencesKey("playlists_json_v1")

    val playlists: Flow<List<MusicPlaylist>> = context.playlistsDataStore.data
        .map { preferences -> decode(preferences[playlistsKey]) }
        .catch { emit(emptyList()) }

    suspend fun createPlaylist(name: String, trackUris: List<String>): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Playlist name cannot be empty" }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        context.playlistsDataStore.edit { preferences ->
            val current = decode(preferences[playlistsKey])
            preferences[playlistsKey] = encode(
                current + MusicPlaylist(
                    id = id,
                    name = cleanName,
                    trackUris = trackUris.distinct(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return id
    }

    suspend fun renamePlaylist(id: String, newName: String) {
        val cleanName = newName.trim()
        require(cleanName.isNotEmpty()) { "Playlist name cannot be empty" }
        context.playlistsDataStore.edit { preferences ->
            preferences[playlistsKey] = encode(
                decode(preferences[playlistsKey]).map { playlist ->
                    if (playlist.id == id) playlist.copy(name = cleanName, updatedAt = System.currentTimeMillis()) else playlist
                }
            )
        }
    }

    suspend fun addTracks(id: String, trackUris: List<String>) {
        if (trackUris.isEmpty()) return
        context.playlistsDataStore.edit { preferences ->
            preferences[playlistsKey] = encode(
                decode(preferences[playlistsKey]).map { playlist ->
                    if (playlist.id == id) playlist.copy(
                        trackUris = (playlist.trackUris + trackUris).distinct(),
                        updatedAt = System.currentTimeMillis()
                    ) else playlist
                }
            )
        }
    }

    suspend fun removeTrack(id: String, trackUri: String) {
        context.playlistsDataStore.edit { preferences ->
            preferences[playlistsKey] = encode(
                decode(preferences[playlistsKey]).map { playlist ->
                    if (playlist.id == id) playlist.copy(
                        trackUris = playlist.trackUris.filterNot { it == trackUri },
                        updatedAt = System.currentTimeMillis()
                    ) else playlist
                }
            )
        }
    }

    suspend fun deletePlaylists(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.playlistsDataStore.edit { preferences ->
            preferences[playlistsKey] = encode(decode(preferences[playlistsKey]).filterNot { it.id in ids })
        }
    }

    private fun encode(playlists: List<MusicPlaylist>): String {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val uris = JSONArray()
            playlist.trackUris.distinct().forEach { uri -> uris.put(uri) }
            val item = JSONObject()
            item.put("id", playlist.id)
            item.put("name", playlist.name)
            item.put("trackUris", uris)
            item.put("createdAt", playlist.createdAt)
            item.put("updatedAt", playlist.updatedAt)
            array.put(item)
        }
        return array.toString()
    }

    private fun decode(json: String?): List<MusicPlaylist> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            val result = ArrayList<MusicPlaylist>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                val uriArray = item.optJSONArray("trackUris") ?: JSONArray()
                val uris = ArrayList<String>(uriArray.length())
                for (uriIndex in 0 until uriArray.length()) {
                    val uri = uriArray.optString(uriIndex)
                    if (uri.isNotBlank()) uris.add(uri)
                }
                result.add(
                    MusicPlaylist(
                        id = id,
                        name = name,
                        trackUris = uris.distinct(),
                        createdAt = item.optLong("createdAt"),
                        updatedAt = item.optLong("updatedAt")
                    )
                )
            }
            result.toList()
        }.getOrDefault(emptyList())
    }
}
