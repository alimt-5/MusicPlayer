package com.example.musicplayer.musicFeature.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.musicplayer.musicFeature.domain.repository.AudioRepository
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class AudioRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRepository {

    override suspend fun getLocalAudioFiles(): List<AudioTrack> =
        withContext(Dispatchers.IO) {

            val tracks = ArrayList<AudioTrack>(1500)

            val collection =
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val projection = buildProjection()

            val selection =
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                        "${MediaStore.Audio.Media.DURATION} > 1000"

            val sortOrder =
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

                val titleColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                val artistColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                val durationColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                val dateAddedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                val albumIdColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                val dataColumn =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    } else {
                        -1
                    }

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)

                    val title =
                        cursor.getString(titleColumn)?.takeIf { it.isNotBlank() }
                            ?: "Unknown"

                    val artist =
                        cursor.getString(artistColumn)?.takeIf { it.isNotBlank() }
                            ?: "Unknown Artist"

                    val duration =
                        cursor.getLong(durationColumn)

                    val dateAdded =
                        cursor.getLong(dateAddedColumn) * 1000L

                    val albumId =
                        cursor.getLong(albumIdColumn)

                    val mediaUri =
                        ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()

                    val albumArtUri =
                        if (albumId > 0L) {
                            ContentUris.withAppendedId(
                                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                                albumId
                            ).toString()
                        } else {
                            null
                        }

                    val path =
                        if (dataColumn != -1) {
                            cursor.getString(dataColumn) ?: ""
                        } else {
                            ""
                        }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

                        if (path.isBlank())
                            continue

                        if (!File(path).exists())
                            continue
                    }

                    tracks.add(
                        AudioTrack(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            mediaUri = mediaUri,
                            duration = duration,
                            path = path,
                            dateAdded = dateAdded,
                            albumArtUri = albumArtUri
                        )
                    )

                }

            }

            tracks
        }

    private fun buildProjection(): Array<String> {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.ALBUM_ID
            )

        } else {

            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.ALBUM_ID
            )

        }
    }
}