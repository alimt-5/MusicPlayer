package com.example.musicplayer.musicFeature.data.playback

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.PlaybackPosition
import com.example.musicplayer.musicFeature.domain.model.PlaybackState
import com.example.musicplayer.musicFeature.domain.model.RepeatMode
import com.example.musicplayer.musicFeature.domain.repository.PlaybackRepository
import com.example.musicplayer.musicFeature.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class Media3PlaybackRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PlaybackRepository {
    private val stateInternal = MutableStateFlow(PlaybackState())
    override val state = stateInternal.asStateFlow()

    private val preferences: SharedPreferences = context.getSharedPreferences("music_playback", Context.MODE_PRIVATE)
    private val lastPlayedTrackInternal = MutableStateFlow(
        preferences.getLong(KEY_LAST_TRACK_ID, INVALID_TRACK_ID).takeIf { it != INVALID_TRACK_ID }
    )
    override val lastPlayedTrackId = lastPlayedTrackInternal.asStateFlow()

    private val lock = ReentrantLock()
    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var queueIds: List<Long> = emptyList()

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@Media3PlaybackRepository.controller === controller) {
                this@Media3PlaybackRepository.controller = null
                this@Media3PlaybackRepository.controllerFuture = null
                queueIds = emptyList()
                stateInternal.value = PlaybackState(currentTrackId = null, isPlaying = false)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            stateInternal.value = stateInternal.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull()
            if (id != null) rememberLastPlayedTrack(id)
            stateInternal.value = stateInternal.value.copy(currentTrackId = id)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            stateInternal.value = stateInternal.value.copy(repeatMode = repeatMode.toDomainRepeatMode())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE) {
                stateInternal.value = stateInternal.value.copy(currentTrackId = null, isPlaying = false)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("Media3Playback", "Player error: ${error.errorCodeName}", error)
            stateInternal.value = stateInternal.value.copy(isPlaying = false)
        }
    }

    override suspend fun connect() {
        controller()
    }

    override suspend fun play(playlist: List<AudioTrack>, index: Int) {
        require(index in playlist.indices) { "Invalid playlist index: $index" }

        val normalized = playlist.distinctBy(AudioTrack::id)
        val selectedId = playlist[index].id
        val actualIndex = normalized.indexOfFirst { it.id == selectedId }
        if (actualIndex < 0) return

        rememberLastPlayedTrack(selectedId)
        val c = controller()
        val ids = normalized.map(AudioTrack::id)
        if (ids != queueIds || c.mediaItemCount != normalized.size) {
            queueIds = ids
            c.setMediaItems(normalized.map(::toMediaItem), actualIndex, 0L)
            c.prepare()
        } else {
            c.seekToDefaultPosition(actualIndex)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
        }
        c.play()
    }

    override suspend fun togglePlayPause() {
        val c = controller()
        if (c.isPlaying) c.pause() else c.play()
    }

    override suspend fun next() {
        controller().seekToNextMediaItem()
    }

    override suspend fun previous() {
        val c = controller()
        if (c.currentPosition > 3_000L) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    override suspend fun seekTo(progress: Float) {
        val c = controller()
        val duration = c.duration
        if (duration > 0L) c.seekTo((progress.coerceIn(0f, 1f) * duration).toLong())
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        val c = controller()
        c.repeatMode = mode.toPlayerRepeatMode()
        stateInternal.value = stateInternal.value.copy(repeatMode = mode)
    }

    override suspend fun pruneQueue(availableTrackIds: Set<Long>) {
        val c = controller()
        if (c.mediaItemCount == 0) {
            queueIds = emptyList()
            return
        }

        val staleIndexes = buildList {
            for (index in 0 until c.mediaItemCount) {
                val id = c.getMediaItemAt(index).mediaId.toLongOrNull() ?: continue
                if (id !in availableTrackIds) add(index)
            }
        }

        staleIndexes.asReversed().forEach(c::removeMediaItem)
        queueIds = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (c.mediaItemCount == 0) {
            c.stop()
            stateInternal.value = stateInternal.value.copy(currentTrackId = null, isPlaying = false)
        }
    }

    override suspend fun stop() {
        val c = controller()
        c.stop()
        c.clearMediaItems()
        queueIds = emptyList()
        stateInternal.value = stateInternal.value.copy(currentTrackId = null, isPlaying = false)
    }

    override fun position(): Flow<PlaybackPosition> = flow {
        while (currentCoroutineContext().isActive) {
            val c = controller
            if (c != null) {
                emit(PlaybackPosition(c.currentPosition.coerceAtLeast(0L), c.duration.coerceAtLeast(0L)))
            } else {
                emit(PlaybackPosition())
            }
            delay(200L.milliseconds)
        }
    }.flowOn(Dispatchers.Main.immediate)

    private fun rememberLastPlayedTrack(id: Long) {
        if (id < 0L) return
        lastPlayedTrackInternal.value = id
        preferences.edit { putLong(KEY_LAST_TRACK_ID, id) }
    }

    private suspend fun controller(): MediaController = withContext(Dispatchers.Main.immediate) {
        controller?.let { return@withContext it }

        val future = lock.withLock {
            controllerFuture ?: MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))
            )
                .setListener(controllerListener)
                .buildAsync()
                .also { controllerFuture = it }
        }

        try {
            val connected = future.await()
            controller?.let { return@withContext it }
            controller = connected
            connected.addListener(listener)
            connected.repeatMode = Player.REPEAT_MODE_ALL
            val currentId = connected.currentMediaItem?.mediaId?.toLongOrNull()
            if (currentId != null) rememberLastPlayedTrack(currentId)
            stateInternal.value = stateInternal.value.copy(
                currentTrackId = currentId,
                isPlaying = connected.isPlaying,
                repeatMode = connected.repeatMode.toDomainRepeatMode()
            )
            connected
        } catch (t: Throwable) {
            lock.withLock { controllerFuture = null }
            throw t
        }
    }

    private fun toMediaItem(track: AudioTrack): MediaItem = MediaItem.Builder()
        .setMediaId(track.id.toString())
        .setUri(track.mediaUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .apply { track.albumArtUri?.let { setArtworkUri(it.toUri()) } }
                .build()
        )
        .build()

    private fun Int.toDomainRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.REPEAT_ONE
        Player.REPEAT_MODE_OFF -> RepeatMode.OFF
        else -> RepeatMode.REPEAT_ALL
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
    }

    private companion object {
        const val KEY_LAST_TRACK_ID = "last_track_id"
        const val INVALID_TRACK_ID = -1L
    }
}
