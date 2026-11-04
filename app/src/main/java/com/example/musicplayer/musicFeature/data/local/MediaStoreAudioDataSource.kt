package com.example.musicplayer.musicFeature.data.local

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.example.musicplayer.musicFeature.core.platform.storage.PartialDeleteException
import com.example.musicplayer.musicFeature.core.platform.storage.RecoverableDeleteException
import com.example.musicplayer.musicFeature.core.platform.storage.RecoverableRenameException
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class MediaStoreAudioDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver = context.contentResolver
    private val manualRefreshes = Channel<Unit>(Channel.CONFLATED)
    private val renamedTitles = context.getSharedPreferences("music_renamed_titles", Context.MODE_PRIVATE)
    private val playbackPreferences = context.getSharedPreferences("music_playback", Context.MODE_PRIVATE)
    private val librarySnapshotFile = File(context.filesDir, "music_library_snapshot.json")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observeAudioTracks(): Flow<List<AudioTrack>> {
        val changes = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    trySend(Unit)
                }
            }
            var registered = false
            try {
                resolver.registerContentObserver(audioCollectionUri(), true, observer)
                registered = true
            } catch (error: Exception) {
                // Some OEM MediaProviders reject observer registration for the aggregate
                // external-volume URI. Do not fail the whole library flow: startup and
                // explicit refresh still perform a direct query.
                Log.w("MediaStoreAudio", "Could not register MediaStore observer; continuing with direct queries", error)
            }
            awaitClose {
                if (registered) runCatching { resolver.unregisterContentObserver(observer) }
            }
        }

        // Coalesce the initial signal, the Activity's first refresh(), and MediaStore
        // notifications into one query instead of cancelling/restarting the first scan.
        val refreshSignals = merge(
            flowOf(Unit),
            changes,
            manualRefreshes.receiveAsFlow()
        ).debounce(250.milliseconds)

        // Always perform one initial query. Relying only on the conflated refresh channel
        // can miss the first refresh if it is sent before upstream collection starts.
        // A cached snapshot is emitted first, then MediaStore replaces it with fresh rows.
        return flow {
            var cacheWasRead = false
            refreshSignals.collect {
                if (!hasAudioReadPermission()) return@collect

                if (!cacheWasRead) {
                    cacheWasRead = true
                    readCachedTracks()?.let { cached -> emit(cached) }
                }

                val freshTracks = queryAudioTracks()
                writeCachedTracks(freshTracks)
                emit(freshTracks)
            }
        }.flowOn(Dispatchers.IO)
    }

    fun refresh() {
        manualRefreshes.trySend(Unit)
    }

    suspend fun deleteTracks(tracks: List<AudioTrack>) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        val uniqueTracks = tracks.distinctBy(AudioTrack::mediaUri)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> deleteOnRPlus(uniqueTracks)
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> deleteOnQ(uniqueTracks)
            else -> deleteOnPie(uniqueTracks)
        }
    }

    suspend fun renameTrack(track: AudioTrack, requestedName: String, afterApproval: Boolean) = withContext(Dispatchers.IO) {
        val uri = track.mediaUri.toUri()
        require(isMediaStoreItemUri(uri)) { "This song is no longer available in the music library. Refresh the list and try again." }

        // MediaStore is the source of truth for the current file name. DATA can be stale
        // on removable volumes and is intentionally used only as a fallback.
        val oldDisplayName = queryDisplayName(uri)
            ?: track.filePath?.takeIf(String::isNotBlank)?.let { File(it).name }
            ?: track.title
        val newDisplayName = buildDisplayName(requestedName, oldDisplayName)
        if (newDisplayName.equals(oldDisplayName, ignoreCase = true)) return@withContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val updated = try {
                updateMediaStoreName(uri, newDisplayName)
            } catch (error: android.app.RecoverableSecurityException) {
                if (afterApproval) {
                    throw IllegalStateException(
                        "Android did not grant write access to this song. Try again or check the file's storage location.",
                        error
                    )
                }
                val request = IntentSenderRequest.Builder(
                    error.userAction.actionIntent.intentSender
                ).build()
                throw RecoverableRenameException(request)
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || afterApproval || hasUriWritePermission(uri)) {
                    throw IllegalStateException(
                        "Android denied permission to rename this song. The file may be on a protected or read-only location.",
                        error
                    )
                }
                val request = MediaStore.createWriteRequest(resolver, listOf(uri))
                throw RecoverableRenameException(
                    IntentSenderRequest.Builder(request.intentSender).build()
                )
            }

            if (!updated) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !afterApproval && !hasUriWritePermission(uri)) {
                    val request = MediaStore.createWriteRequest(resolver, listOf(uri))
                    throw RecoverableRenameException(
                        IntentSenderRequest.Builder(request.intentSender).build()
                    )
                }
                throw IllegalStateException(
                    "MediaStore could not rename this song. It may have been moved, removed, or stored in a read-only location."
                )
            }
        } else {
            requireLegacyWritePermission()
            renameLegacyFile(track, uri, newDisplayName)
        }

        val title = newDisplayName.substringBeforeLast('.', newDisplayName)
        val editor = renamedTitles.edit().putString(idTitleKey(track.id), title)
        pathTitleKey(track.filePath)?.let(editor::remove)
        pathTitleKey(targetPath(track.filePath, newDisplayName))?.let { editor.putString(it, title) }
        editor.apply()
    }

    private fun updateMediaStoreName(uri: Uri, displayName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        }
        val updatedRows = resolver.update(uri, values, null, null)
        return updatedRows > 0 || queryDisplayName(uri).equals(displayName, ignoreCase = true)
    }

    private fun renameLegacyFile(track: AudioTrack, uri: Uri, newName: String) {
        val oldPath = track.filePath?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("The file path is unavailable on this Android version")
        val oldFile = File(oldPath)
        if (!oldFile.exists()) {
            throw IllegalStateException("The original file no longer exists. Refresh the music library and try again.")
        }
        val parent = oldFile.parentFile
            ?: throw IllegalStateException("The file's parent directory is unavailable")
        val newFile = File(parent, newName)
        require(!newFile.exists() || newFile.absolutePath == oldFile.absolutePath) {
            "A file with that name already exists in this folder"
        }

        if (oldFile.absolutePath != newFile.absolutePath && !oldFile.renameTo(newFile)) {
            throw IllegalStateException(
                "Android could not rename this file. Check storage permission and whether the file is read-only."
            )
        }

        // The file has already been moved on disk. Remove the stale MediaStore row,
        // then let the scanner publish the new path and URI.
        runCatching { resolver.delete(uri, null, null) }
        MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null) { _, _ -> refresh() }
    }

    private fun queryDisplayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
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
    private fun deleteOnRPlus(tracks: List<AudioTrack>) {
        val validTracks = tracks.filter { isMediaStoreItemUri(it.mediaUri.toUri()) }
        val rejectedTracks = tracks.filterNot { isMediaStoreItemUri(it.mediaUri.toUri()) }
        val completedIds = LinkedHashSet<Long>()
        val protectedTracks = ArrayList<AudioTrack>()
        val failedTracks = ArrayList<AudioTrack>()

        // Delete immediately when Android already grants write access. Calling
        // createDeleteRequest() with these items would still show them in the system
        // confirmation, so only protected items are sent to that request.
        for (track in validTracks) {
            val uri = track.mediaUri.toUri()
            if (!hasUriWritePermission(uri)) {
                protectedTracks += track
                continue
            }
            try {
                val deletedRows = resolver.delete(uri, null, null)
                when {
                    deletedRows > 0 -> completedIds += track.id
                    mediaRowState(uri) == MediaRowState.MISSING -> completedIds += track.id
                    else -> failedTracks += track
                }
            } catch (error: SecurityException) {
                // Permission may have changed between the permission check and delete.
                protectedTracks += track
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failedTracks += track
            }
        }

        if (protectedTracks.isNotEmpty()) {
            require(protectedTracks.size <= 2_000) {
                "Android allows up to 2000 songs in one delete confirmation. Select fewer songs and try again."
            }
            val request = try {
                MediaStore.createDeleteRequest(
                    resolver,
                    protectedTracks.map { it.mediaUri.toUri() }
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw PartialDeleteException(
                    completedIds,
                    protectedTracks.map(AudioTrack::title) + failedTracks.map(AudioTrack::title) + rejectedTracks.map(AudioTrack::title)
                )
            }

            throw RecoverableDeleteException(
                request = IntentSenderRequest.Builder(request.intentSender).build(),
                retryAfterApproval = false,
                completedTrackIds = completedIds,
                requestedTrackIds = protectedTracks.mapTo(LinkedHashSet(), AudioTrack::id),
                rejectedTrackIds = (failedTracks + rejectedTracks).mapTo(LinkedHashSet(), AudioTrack::id),
                failedTrackTitles = failedTracks.map(AudioTrack::title) + rejectedTracks.map(AudioTrack::title)
            )
        }

        if (failedTracks.isNotEmpty() || rejectedTracks.isNotEmpty()) {
            throw PartialDeleteException(
                completedIds,
                failedTracks.map(AudioTrack::title) + rejectedTracks.map(AudioTrack::title)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteOnQ(tracks: List<AudioTrack>) {
        val completedIds = LinkedHashSet<Long>()
        val failed = ArrayList<AudioTrack>()

        for (track in tracks) {
            val uri = track.mediaUri.toUri()
            if (!isMediaStoreItemUri(uri)) {
                failed += track
                continue
            }
            try {
                val deletedRows = resolver.delete(uri, null, null)
                when {
                    deletedRows > 0 -> completedIds += track.id
                    mediaRowState(uri) == MediaRowState.MISSING -> completedIds += track.id
                    else -> failed += track
                }
            } catch (error: android.app.RecoverableSecurityException) {
                val request = IntentSenderRequest.Builder(
                    error.userAction.actionIntent.intentSender
                ).build()
                throw RecoverableDeleteException(
                    request = request,
                    retryAfterApproval = true,
                    completedTrackIds = completedIds
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed += track
            }
        }

        if (failed.isNotEmpty()) {
            throw PartialDeleteException(completedIds, failed.map(AudioTrack::title))
        }
    }

    private fun deleteOnPie(tracks: List<AudioTrack>) {
        requireLegacyWritePermission()
        val completedIds = LinkedHashSet<Long>()
        val failed = ArrayList<AudioTrack>()

        for (track in tracks) {
            val uri = track.mediaUri.toUri()
            if (!isMediaStoreItemUri(uri)) {
                failed += track
                continue
            }
            try {
                val deletedRows = resolver.delete(uri, null, null)
                if (deletedRows > 0 || mediaRowState(uri) == MediaRowState.MISSING) {
                    completedIds += track.id
                    continue
                }

                // API 28 still uses legacy shared-storage permissions. File.delete is
                // only a fallback here, after WRITE_EXTERNAL_STORAGE was checked.
                val removedFromDisk = track.filePath?.let(::deleteLegacyFile) == true
                if (removedFromDisk) {
                    runCatching { resolver.delete(uri, null, null) }
                    completedIds += track.id
                } else {
                    failed += track
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val removedFromDisk = track.filePath?.let(::deleteLegacyFile) == true
                if (removedFromDisk) completedIds += track.id else failed += track
            }
        }

        if (failed.isNotEmpty()) {
            throw PartialDeleteException(completedIds, failed.map(AudioTrack::title))
        }
    }

    private enum class MediaRowState { EXISTS, MISSING, UNKNOWN }

    private fun mediaRowState(uri: Uri): MediaRowState {
        return try {
            val cursor = resolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null
            ) ?: return MediaRowState.UNKNOWN
            cursor.use { if (it.moveToFirst()) MediaRowState.EXISTS else MediaRowState.MISSING }
        } catch (_: SecurityException) {
            MediaRowState.UNKNOWN
        } catch (_: Exception) {
            MediaRowState.UNKNOWN
        }
    }

    private fun deleteLegacyFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return true
        if (file.delete()) return !file.exists()
        return false
    }

    private fun isMediaStoreItemUri(uri: Uri): Boolean =
        uri.scheme == "content" &&
            uri.authority == "media" &&
            uri.lastPathSegment?.toLongOrNull() != null

    @RequiresApi(Build.VERSION_CODES.R)
    private fun hasUriWritePermission(uri: Uri): Boolean = context.checkUriPermission(
        uri,
        Process.myPid(),
        Process.myUid(),
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    ) == PackageManager.PERMISSION_GRANTED

    private fun requireLegacyWritePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException(
                "Android 9 requires storage write permission to rename or delete music files. Grant storage access and try again."
            )
        }
    }

    private fun readCachedTracks(): List<AudioTrack>? {
        if (!librarySnapshotFile.isFile) return null
        return runCatching {
            val root = JSONObject(librarySnapshotFile.readText(Charsets.UTF_8))
            if (root.optInt("version") != 1) return null
            val items = root.optJSONArray("tracks") ?: return null
            buildList(items.length()) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optLong("id", -1L)
                    val title = item.optString("title").takeIf(String::isNotBlank) ?: continue
                    val artist = item.optString("artist").takeIf(String::isNotBlank) ?: "Unknown artist"
                    val mediaUri = item.optString("mediaUri").takeIf(String::isNotBlank) ?: continue
                    val normalizedTitle = item.optString("normalizedTitle").takeIf(String::isNotBlank)
                        ?: title.lowercase(Locale.ROOT)
                    add(
                        AudioTrack(
                            id = id,
                            title = title,
                            artist = artist,
                            mediaUri = mediaUri,
                            durationMs = item.optLong("durationMs"),
                            dateAddedMs = item.optLong("dateAddedMs"),
                            albumArtUri = item.nullableString("albumArtUri"),
                            filePath = item.nullableString("filePath"),
                            normalizedTitle = normalizedTitle,
                            searchText = item.optString("searchText").takeIf(String::isNotBlank)
                                ?: "$normalizedTitle ${artist.lowercase(Locale.ROOT)}"
                        )
                    )
                }
            }
        }.onFailure { error ->
            Log.w("MediaStoreAudio", "Ignoring invalid library snapshot", error)
        }.getOrNull()
    }

    private fun writeCachedTracks(tracks: List<AudioTrack>) {
        runCatching {
            val items = JSONArray()
            tracks.forEach { track ->
                items.put(
                    JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("mediaUri", track.mediaUri)
                        put("durationMs", track.durationMs)
                        put("dateAddedMs", track.dateAddedMs)
                        put("albumArtUri", track.albumArtUri ?: JSONObject.NULL)
                        put("filePath", track.filePath ?: JSONObject.NULL)
                        put("normalizedTitle", track.normalizedTitle)
                        put("searchText", track.searchText)
                    }
                )
            }
            val root = JSONObject().put("version", 1).put("tracks", items)
            val tempFile = File(librarySnapshotFile.parentFile, "${librarySnapshotFile.name}.tmp")
            tempFile.writeText(root.toString(), Charsets.UTF_8)
            if (!tempFile.renameTo(librarySnapshotFile)) {
                tempFile.copyTo(librarySnapshotFile, overwrite = true)
                tempFile.delete()
            }
        }.onFailure { error ->
            Log.w("MediaStoreAudio", "Could not save library snapshot", error)
        }
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeUnless { it.isBlank() || it == "null" }
    }

    private fun hasAudioReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun queryAudioTracks(): List<AudioTrack> {
        try {
            return queryAudioTracksInternal(
                collection = audioCollectionUri(),
                projection = buildProjection(),
                selection = buildSelection(),
                allowPathlessFingerprint = true
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            // Never turn a permission failure into an empty library; the caller keeps its
            // last cached snapshot and reports the actual cause.
            Log.e("MediaStoreAudio", "Audio library read permission was denied", error)
            throw error
        } catch (primaryError: Exception) {
            Log.w("MediaStoreAudio", "Primary MediaStore query failed; trying compatibility query", primaryError)
            try {
                // Some OEM MediaProvider implementations reject newer optional columns
                // (VOLUME_NAME, RELATIVE_PATH, DATA) or selection terms such as IS_TRASHED.
                // Retry with the stable audio columns and no optional selection predicates.
                return queryAudioTracksInternal(
                    collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection = buildFallbackProjection(),
                    selection = "${MediaStore.Audio.Media.DURATION} > 1000 AND ${MediaStore.Audio.Media.SIZE} > 0",
                    allowPathlessFingerprint = false
                )
            } catch (fallbackError: CancellationException) {
                throw fallbackError
            } catch (fallbackError: Exception) {
                fallbackError.addSuppressed(primaryError)
                Log.e("MediaStoreAudio", "Both primary and compatibility MediaStore queries failed", fallbackError)
                throw IllegalStateException(
                    "Could not query audio files from MediaStore: ${fallbackError.message ?: fallbackError.javaClass.simpleName}",
                    fallbackError
                )
            }
        }
    }

    private fun queryAudioTracksInternal(
        collection: Uri,
        projection: Array<String>,
        selection: String?,
        allowPathlessFingerprint: Boolean
    ): List<AudioTrack> {
        val tracks = ArrayList<AudioTrack>()
        // Use real physical paths as the primary duplicate key. Metadata fingerprints are
        // used only when the provider does not expose a path; otherwise two different files
        // in separate folders can have identical title/size/duration and must both remain.
        val pathToIndex = HashMap<String, Int>()
        val pathlessFingerprintToIndex = HashMap<String, Int>()
        // Duplicate rows are uncommon, but checking the same content URI repeatedly is costly.
        // Cache the inexpensive open-descriptor result for this scan only.
        val openableUriCache = HashMap<String, Boolean>()
        val lastPlayedId = playbackPreferences.getLong("last_track_id", -1L)

        val cursor = resolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media._ID} ASC"
        ) ?: throw IllegalStateException("MediaStore returned no cursor while reading the music library")

        cursor.use { cursor ->
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
                    ?: relativePath?.takeIf(String::isNotBlank)?.let { path ->
                        relativeMediaPathIdentity(volumeName, path, displayName)
                    }
                val fallbackIdentity = fallbackIdentity(
                    displayName = displayName,
                    size = size,
                    durationMs = durationMs,
                    title = rawTitle,
                    artist = artist
                )
                val existingIndex = when {
                    pathIdentity != null -> pathToIndex[pathIdentity]
                    allowPathlessFingerprint -> pathlessFingerprintToIndex[fallbackIdentity]
                    else -> null
                }
                if (existingIndex == null) {
                    val newIndex = tracks.size
                    tracks += track
                    if (pathIdentity != null) pathToIndex[pathIdentity] = newIndex
                    else if (allowPathlessFingerprint) pathlessFingerprintToIndex[fallbackIdentity] = newIndex
                } else {
                    val existing = tracks[existingIndex]
                    // Validate only duplicate candidates. Do not instantiate MediaExtractor here:
                    // decoder probing for two rows per duplicate made the first library scan slow.
                    // Opening a file descriptor is sufficient to reject stale MediaStore rows
                    // (including ENOENT) without decoding the audio stream.
                    val candidateIsLastPlayed = track.id == lastPlayedId
                    val existingIsLastPlayed = existing.id == lastPlayedId
                    val shouldReplace = if (candidateIsLastPlayed && !existingIsLastPlayed) {
                        // Prefer the last-played duplicate when it is still accessible.
                        isMediaUriOpenable(track.mediaUri, openableUriCache)
                    } else {
                        // Keep the current row unless it is stale; only then test its duplicate.
                        !isMediaUriOpenable(existing.mediaUri, openableUriCache) &&
                            isMediaUriOpenable(track.mediaUri, openableUriCache)
                    }
                    if (shouldReplace) tracks[existingIndex] = track
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
        // absolutePath is lexical and avoids filesystem canonicalization for every song.
        val normalized = File(path).absolutePath.replace(File.separatorChar, '/').trimEnd('/')
        return "path:${normalized.trim().lowercase(Locale.ROOT)}"
    }

    private fun relativeMediaPathIdentity(volumeName: String?, relativePath: String, displayName: String): String {
        val normalizedRelativePath = relativePath.replace('\\', '/').trim('/')
        val normalizedName = displayName.trim().lowercase(Locale.ROOT)
        val normalizedVolume = volumeName.orEmpty().lowercase(Locale.ROOT)
        return "relative:$normalizedVolume/$normalizedRelativePath/$normalizedName".lowercase(Locale.ROOT)
    }

    private fun fallbackIdentity(
        displayName: String,
        size: Long,
        durationMs: Long,
        title: String,
        artist: String
    ): String {
        // This fallback is used only when neither DATA nor RELATIVE_PATH is available.
        // It helps collapse duplicate provider rows, but physical/relative paths always win
        // so same-named files in different folders are not merged accidentally.
        val normalizedName = displayName.trim().lowercase(Locale.ROOT)
        val normalizedTitle = title.trim().lowercase(Locale.ROOT)
        val normalizedArtist = artist.trim().lowercase(Locale.ROOT)
        return "fingerprint:$normalizedName|$size|$durationMs|$normalizedTitle|$normalizedArtist"
    }

    private fun isMediaUriOpenable(uriString: String, cache: MutableMap<String, Boolean>): Boolean =
        cache.getOrPut(uriString) {
            runCatching {
                resolver.openFileDescriptor(uriString.toUri(), "r")?.use { true } ?: false
            }.getOrDefault(false)
        }

    private fun buildSelection(): String = buildList {
        add("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        add("${MediaStore.Audio.Media.DURATION} > 1000")
        add("${MediaStore.Audio.Media.SIZE} > 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.Audio.Media.IS_PENDING} = 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.Audio.Media.IS_TRASHED} = 0")
    }.joinToString(" AND ")

    private fun buildFallbackProjection(): Array<String> = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.MIME_TYPE
    )

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
            runCatching { MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) }
                .getOrDefault(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
}
