package com.example.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.musicplayer.domain.AudioRepository
import com.example.musicplayer.domain.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioRepositoryImpl(private val context: Context) : AudioRepository {
    override suspend fun getLocalAudioFiles(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID
        )

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn) ?: continue
                if (!File(path).exists()) {
                    android.util.Log.w("AudioRepo", "File not found: $path")
                    continue
                }

                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val duration = cursor.getLong(durationColumn)
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000L
                val albumId = cursor.getLong(albumIdColumn)
                val contentUri =
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        .toString()
                val albumArtUri = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId
                    ).toString()
                } else null

                audioList.add(
                    AudioTrack(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        mediaUri = contentUri,
                        duration = duration,
                        path = path,
                        dateAdded = dateAdded,
                        albumArtUri = albumArtUri
                    )
                )
            }
        }
        audioList
    }
}