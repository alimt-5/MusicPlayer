package com.example.musicplayer.musicFeature.data.local

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.example.musicplayer.musicFeature.core.platform.storage.RecoverableDeleteException
import com.example.musicplayer.musicFeature.core.platform.storage.RecoverableRenameException
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.microseconds

@Singleton
class MediaStoreAudioDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver = context.contentResolver
    private val manualRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val renamedTitles = context.getSharedPreferences("music_renamed_titles", Context.MODE_PRIVATE)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observeAudioTracks(): Flow<List<AudioTrack>> {
        val changes = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    trySend(Unit)
                }
            }
            resolver.registerContentObserver(audioCollectionUri(), true, observer)
            trySend(Unit)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }

        return merge(changes, manualRefreshes)
            .onStart { emit(Unit) }
            .debounce(200.microseconds)
            .mapLatest { queryAudioTracks() }
            .flowOn(Dispatchers.IO)
    }

    fun refresh() {
        manualRefreshes.tryEmit(Unit)
    }

    suspend fun deleteTracks(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> deleteOnRPlus(tracks.map { it.mediaUri.toUri() })
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> deleteOnQ(tracks.single())
            else -> deleteOnPie(tracks)
        }
    }

    suspend fun renameTrack(track: AudioTrack, requestedName: String, afterApproval: Boolean) = withContext(Dispatchers.IO) {
        val uri = track.mediaUri.toUri()
        val oldDisplayName = queryDisplayName(uri) ?: track.title
        val newDisplayName = buildDisplayName(requestedName, oldDisplayName)
        require(newDisplayName.isNotBlank()) { "The file name cannot be empty" }
        if (newDisplayName.equals(oldDisplayName, ignoreCase = true)) return@withContext

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !afterApproval -> {
                val request = MediaStore.createWriteRequest(resolver, listOf(uri))
                throw RecoverableRenameException(
                    request = androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                )
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                try {
                    updateMediaStoreName(uri, newDisplayName)
                } catch (e: android.app.RecoverableSecurityException) {
                    val request = androidx.activity.result.IntentSenderRequest.Builder(
                        e.userAction.actionIntent.intentSender
                    ).build()
                    throw RecoverableRenameException(request)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> updateMediaStoreName(uri, newDisplayName)
            else -> renameLegacyFile(track, uri, newDisplayName)
        }

        val title = newDisplayName.substringBeforeLast('.', newDisplayName)
        val editor = renamedTitles.edit().putString(idTitleKey(track.id), title)
        pathTitleKey(targetPath(track.filePath, newDisplayName))?.let { editor.putString(it, title) }
        editor.apply()
        refresh()
    }

    private fun updateMediaStoreName(uri: Uri, displayName: String) {
        val title = displayName.substringBeforeLast('.', displayName)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, title)
        }
        val updatedRows = resolver.update(uri, values, null, null)
        if (updatedRows <= 0 && !queryDisplayName(uri).equals(displayName, ignoreCase = true)) {
            throw IllegalStateException("Android could not rename this audio file")
        }
    }

    private fun renameLegacyFile(track: AudioTrack, uri: Uri, newName: String) {
        val oldPath = track.filePath?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("The file path is unavailable on this Android version")
        val oldFile = File(oldPath)
        val parent = oldFile.parentFile ?: throw IllegalStateException("The file parent directory is unavailable")
        val newFile = File(parent, newName)
        require(!newFile.exists() || newFile.absolutePath == oldFile.absolutePath) { "A file with that name already exists" }

        if (oldFile.absolutePath != newFile.absolutePath && !oldFile.renameTo(newFile)) {
            throw IllegalStateException("Android could not rename this audio file")
        }

        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DATA, newFile.absolutePath)
                put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
                put(MediaStore.Audio.Media.TITLE, newName.substringBeforeLast('.', newName))
            }
            resolver.update(uri, values, null, null)
        }

        val title = newName.substringBeforeLast('.', newName)
        val editor = renamedTitles.edit().putString(idTitleKey(track.id), title)
        pathTitleKey(newFile.absolutePath)?.let { editor.putString(it, title) }
        editor.apply()

        MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null) { _, _ -> refresh() }
    }

    private fun queryDisplayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.takeUnless(String::isEmpty) else null
    }

    private fun buildDisplayName(requestedName: String, oldDisplayName: String): String {
        val requested = requestedName.trim()
        require(requested.isNotEmpty()) { "The file name cannot be empty" }
        require(requested.none { it == '/' || it == '\\' || it == '\u0000' }) { "The file name contains invalid characters" }

        val dot = oldDisplayName.lastIndexOf('.')
        val extension = oldDisplayName.substring(dot + 1).takeIf { dot > 0 && it.isNotBlank() }
        if (extension == null) return requested

        val requestedDot = requested.lastIndexOf('.')
        val baseName = if (requestedDot > 0) requested.substring(0, requestedDot) else requested
        require(baseName.isNotBlank()) { "The file name cannot be empty" }
        return "$baseName.$extension"
    }

    private fun targetPath(currentPath: String?, newDisplayName: String): String? {
        if (currentPath.isNullOrBlank()) return null
        val file = File(currentPath)
        return file.parentFile?.let { File(it, newDisplayName).absolutePath }
    }

    private fun idTitleKey(id: Long) = "id:$id"

    private fun pathTitleKey(path: String?): String? = path?.let {
        val normalized = runCatching { File(it).canonicalPath }.getOrDefault(it)
        "path:${normalized.lowercase(Locale.ROOT)}"
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun deleteOnRPlus(uris: List<Uri>) {
        require(uris.size <= 2_000) { "A single Android 11+ delete request supports at most 2000 items" }
        val request = MediaStore.createDeleteRequest(resolver, uris)
        throw RecoverableDeleteException(
            request = androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build(),
            retryAfterApproval = false
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteOnQ(track: AudioTrack) {
        val uri = track.mediaUri.toUri()
        try {
            val deletedRows = resolver.delete(uri, null, null)
            if (deletedRows > 0) {
                if (track.filePath.isNullOrBlank() || !File(track.filePath).exists()) return
                if (deleteLegacyFile(track.filePath)) return
                throw IllegalStateException("Could not fully delete ${track.title}")
            }
            if (!mediaRowExists(uri)) return
            if (track.filePath != null && deleteLegacyFile(track.filePath)) return
            throw IllegalStateException("Could not delete ${track.title}")
        } catch (e: android.app.RecoverableSecurityException) {
            val request = androidx.activity.result.IntentSenderRequest.Builder(
                e.userAction.actionIntent.intentSender
            ).build()
            throw RecoverableDeleteException(request, retryAfterApproval = true)
        }
    }

    private fun deleteOnPie(tracks: List<AudioTrack>) {
        for (track in tracks) {
            val uri = track.mediaUri.toUri()
            val deletedRows = resolver.delete(uri, null, null)
            if (deletedRows > 0 && track.filePath.isNullOrBlank()) continue

            if (track.filePath != null) {
                if (!deleteLegacyFile(track.filePath)) {
                    throw IllegalStateException("Could not fully delete ${track.title}")
                }
            } else if (deletedRows <= 0) {
                throw IllegalStateException("Could not delete ${track.title}")
            }
        }
    }

    private fun deleteLegacyFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return true
        if (file.delete()) return !file.exists()
        return false
    }

    private fun mediaRowExists(uri: Uri): Boolean {
        return resolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media._ID),
            null,
            null,
            null
        )?.use { it.moveToFirst() } == true
    }

    private fun queryAudioTracks(): List<AudioTrack> {
        return runCatching {
            queryAudioTracksInternal()
        }.getOrElse { error ->
            if (error is SecurityException) emptyList() else throw error
        }
    }

    private fun queryAudioTracksInternal(): List<AudioTrack> {
        val collection = audioCollectionUri()
        val tracks = ArrayList<AudioTrack>()
        val identityToIndex = HashMap<String, Int>()

        resolver.query(
            collection,
            buildProjection(),
            buildSelection(),
            null,
            "${MediaStore.Audio.Media._ID} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1
            val volumeNameColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
            } else -1

            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idColumn)
                val rawTitle = cursor.getString(titleColumn)?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unknown title"
                val artist = cursor.getString(artistColumn)?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unknown artist"
                val durationMs = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val mime = cursor.getString(mimeColumn)
                val displayName = cursor.getString(displayNameColumn)?.trim().takeUnless { it.isNullOrEmpty() } ?: rawTitle
                val filePath = pathColumn.takeIf { it >= 0 }
                    ?.let(cursor::getString)
                    ?.trim()
                    ?.takeUnless(String::isEmpty)
                val relativePath = relativePathColumn.takeIf { it >= 0 }?.let(cursor::getString)
                val volumeName = volumeNameColumn.takeIf { it >= 0 }?.let(cursor::getString)
                val id = stableTrackId(volumeName, mediaStoreId)
                val itemCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !volumeName.isNullOrBlank()) {
                    MediaStore.Audio.Media.getContentUri(volumeName)
                } else {
                    collection
                }
                val mediaUri = ContentUris.withAppendedId(itemCollection, mediaStoreId).toString()
                val albumId = cursor.getLong(albumIdColumn)
                val albumArtUri = albumId.takeIf { it > 0L }?.let {
                    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, it).toString()
                }

                if (mime != null && !mime.lowercase(Locale.ROOT).startsWith("audio/")) continue
                if (size <= 0L || durationMs <= 1_000L) continue
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && filePath != null && !File(filePath).exists()) continue
                // MediaStore can occasionally retain a row after the underlying file has been
                // moved or deleted. Do not expose a URI that ExoPlayer cannot open.
                if (!canOpenMediaUri(mediaUri)) continue

                val overrideTitle = renamedTitles.getString(idTitleKey(id), null)
                    ?: pathTitleKey(filePath)?.let { key -> renamedTitles.getString(key, null) }
                val title = overrideTitle ?: rawTitle
                val normalizedTitle = title.lowercase(Locale.ROOT)
                val searchText = "$normalizedTitle ${artist.lowercase(Locale.ROOT)}"
                val track = AudioTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    mediaUri = mediaUri,
                    durationMs = durationMs,
                    dateAddedMs = cursor.getLong(dateAddedColumn) * 1_000L,
                    albumArtUri = albumArtUri,
                    filePath = filePath,
                    normalizedTitle = normalizedTitle,
                    searchText = searchText
                )

                val pathIdentity = physicalPathIdentity(filePath)
                val fallbackIdentity = fallbackIdentity(
                    volumeName = volumeName,
                    relativePath = relativePath,
                    displayName = displayName,
                    size = size,
                    durationMs = durationMs,
                    title = rawTitle,
                    artist = artist
                )
                val existingIndex = pathIdentity?.let(identityToIndex::get) ?: identityToIndex[fallbackIdentity]
                if (existingIndex == null) {
                    val newIndex = tracks.size
                    tracks += track
                    pathIdentity?.let { identityToIndex[it] = newIndex }
                    identityToIndex[fallbackIdentity] = newIndex
                } else {
                    identityToIndex[fallbackIdentity] = existingIndex
                    pathIdentity?.let { identityToIndex[it] = existingIndex }
                    val existing = tracks[existingIndex]
                    if (!canReadAudio(existing.mediaUri) && canReadAudio(track.mediaUri)) {
                        tracks[existingIndex] = track
                    }
                }
            }
        }

        return tracks
    }

    private fun stableTrackId(volumeName: String?, mediaStoreId: Long): Long {
        if (volumeName.isNullOrBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return mediaStoreId
        val volumeHash = volumeName.hashCode().toLong() and 0x7fffffffL
        return (volumeHash shl 32) or (mediaStoreId and 0xffffffffL)
    }

    private fun physicalPathIdentity(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val normalized = runCatching { File(path).canonicalPath }.getOrDefault(path)
        return "path:${normalized.trim().lowercase(Locale.ROOT)}"
    }

    private fun fallbackIdentity(
        volumeName: String?,
        relativePath: String?,
        displayName: String,
        size: Long,
        durationMs: Long,
        title: String,
        artist: String
    ): String {
        val volume = volumeName.orEmpty().lowercase(Locale.ROOT)
        val normalizedName = displayName.trim().lowercase(Locale.ROOT)
        val normalizedTitle = title.trim().lowercase(Locale.ROOT)
        val normalizedArtist = artist.trim().lowercase(Locale.ROOT)
        val location = relativePath?.trim('/')?.lowercase(Locale.ROOT)
        return if (!location.isNullOrBlank()) {
            "relative:$volume/$location/$normalizedName|$size|$durationMs|$normalizedTitle|$normalizedArtist"
        } else {
            "fingerprint:$volume/$normalizedName|$size|$durationMs|$normalizedTitle|$normalizedArtist"
        }
    }

    private fun canOpenMediaUri(uriString: String): Boolean = runCatching {
        resolver.openFileDescriptor(uriString.toUri(), "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun canReadAudio(uriString: String): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uriString.toUri(), null)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun buildSelection(): String = buildList {
        add("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        add("${MediaStore.Audio.Media.DURATION} > 1000")
        add("${MediaStore.Audio.Media.SIZE} > 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.Audio.Media.IS_PENDING} = 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.Audio.Media.IS_TRASHED} = 0")
    }.joinToString(" AND ")

    private fun buildProjection(): Array<String> = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.DATE_ADDED)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.DISPLAY_NAME)
        add(MediaStore.Audio.Media.MIME_TYPE)
        add(MediaStore.Audio.Media.DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Audio.Media.RELATIVE_PATH)
            add(MediaStore.MediaColumns.VOLUME_NAME)
        }
    }.toTypedArray()

    private fun audioCollectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
}
