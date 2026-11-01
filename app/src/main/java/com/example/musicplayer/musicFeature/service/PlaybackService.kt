package com.example.musicplayer.musicFeature.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.musicFeature.core.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playbackPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkLoadJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        playbackPlayer = player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) loadArtworkForNotification(player, mediaItem)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "Skipping an unreadable media item: ${error.errorCodeName}", error)
                skipFailedMediaItem(player)
            }
        })

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * MediaStore rows can occasionally point to files that disappear between scanning and
     * playback. Remove only the failing queue entry and continue with the next item so one
     * stale duplicate cannot terminate the entire playback service.
     */
    private fun skipFailedMediaItem(player: ExoPlayer) {
        if (player.mediaItemCount == 0) {
            player.stop()
            return
        }

        val failedIndex = player.currentMediaItemIndex
        if (failedIndex == C.INDEX_UNSET || failedIndex !in 0 until player.mediaItemCount) {
            player.stop()
            return
        }

        player.removeMediaItem(failedIndex)
        val remainingCount = player.mediaItemCount
        if (remainingCount == 0) {
            player.stop()
            return
        }

        val nextIndex = when {
            failedIndex < remainingCount -> failedIndex
            player.repeatMode == Player.REPEAT_MODE_ALL || player.repeatMode == Player.REPEAT_MODE_ONE -> 0
            else -> {
                // No next item exists and repeat is disabled. Do not restart an earlier song.
                player.clearMediaItems()
                player.stop()
                return
            }
        }

        player.seekToDefaultPosition(nextIndex)
        player.prepare()
        player.play()
    }

    /** Loads artwork as bytes for the Media3 notification without doing I/O during library scan. */
    private fun loadArtworkForNotification(player: ExoPlayer, item: MediaItem) {
        if (item.mediaMetadata.artworkData?.isNotEmpty() == true) return
        val audioUri = item.localConfiguration?.uri ?: return
        val mediaId = item.mediaId
        val artworkUri = item.mediaMetadata.artworkUri

        artworkLoadJob?.cancel()
        artworkLoadJob = serviceScope.launch {
            val artworkBytes = withContext(Dispatchers.IO) {
                runCatching { readArtworkBytes(audioUri, artworkUri) }.getOrNull()
            } ?: return@launch

            val currentIndex = player.currentMediaItemIndex
            val currentItem = player.currentMediaItem ?: return@launch
            if (currentIndex == C.INDEX_UNSET || currentItem.mediaId != mediaId) return@launch

            val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            player.replaceMediaItem(
                currentIndex,
                currentItem.buildUpon().setMediaMetadata(updatedMetadata).build()
            )
        }
    }

    private fun readArtworkBytes(audioUri: Uri, artworkUri: Uri?): ByteArray? {
        var bitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bitmap = runCatching {
                    contentResolver.loadThumbnail(audioUri, Size(512, 512), null)
                }.getOrNull()
                if (bitmap == null && artworkUri != null) {
                    bitmap = runCatching {
                        contentResolver.loadThumbnail(artworkUri, Size(512, 512), null)
                    }.getOrNull()
                }
            }
            if (bitmap == null && artworkUri != null) {
                bitmap = contentResolver.openInputStream(artworkUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
            val source = bitmap ?: return null
            val longestSide = maxOf(source.width, source.height)
            val outputBitmap = if (longestSide > 512) {
                val scale = 512f / longestSide
                scaledBitmap = Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                scaledBitmap ?: source
            } else {
                source
            }

            return ByteArrayOutputStream().use { output ->
                if (outputBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) output.toByteArray() else null
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("PlaybackService", "Could not load album artwork", error)
            return null
        } finally {
            if (scaledBitmap != null && scaledBitmap !== bitmap) scaledBitmap?.recycle()
            bitmap?.recycle()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        artworkLoadJob?.cancel()
        serviceScope.cancel()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        } ?: playbackPlayer?.release()
        mediaSession = null
        playbackPlayer = null
        super.onDestroy()
    }
}
