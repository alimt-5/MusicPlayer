package com.example.musicplayer.presentation.viewModel

import android.annotation.SuppressLint
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.PlaybackService
import com.example.musicplayer.domain.AudioTrack
import com.example.musicplayer.domain.GetAudioTracksUseCase
import com.example.musicplayer.presentation.HomeUiState
import com.example.musicplayer.presentation.RepeatMode
import com.example.musicplayer.presentation.SortType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@ExperimentalMaterial3Api
class AudioViewModel(
    private val getAudioTracksUseCase: GetAudioTracksUseCase,
    private val context: Context,
    private val deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
) : ViewModel() {

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _sortType = MutableStateFlow(SortType.ALPHABETICAL)
    private val _repeatMode = MutableStateFlow(RepeatMode.REPEAT_ALL)
    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    private val _isPlaying = MutableStateFlow(false)
    private var mediaController: MediaController? = null
    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()
    private var pendingDeleteTrack: AudioTrack? = null
    private val filteredTracks: StateFlow<List<AudioTrack>> = combine(
        _tracks,
        _searchQuery,
        _sortType
    ) { tracks, query, sortType ->
        val filtered =
            if (query.isBlank()) {
                tracks
            } else {
                tracks.filter {
                    it.title.contains(query, true) ||
                            it.artist.contains(query, true)
                }
            }

        when (sortType) {
            SortType.ALPHABETICAL ->
                filtered.sortedBy { it.title.lowercase() }

            SortType.DATE_ADDED ->
                filtered.sortedByDescending { it.dateAdded }
        }

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val uiState: StateFlow<HomeUiState> =
        combine(
            filteredTracks,
            _searchQuery,
            _currentTrack,
            _isPlaying,
            _sortType,
            _repeatMode
        ) {
            HomeUiState(
                tracks = filteredTracks.value,
                searchQuery = _searchQuery.value,
                currentTrack = _currentTrack.value,
                isPlaying = _isPlaying.value,
                sortType = _sortType.value,
                repeatMode = _repeatMode.value
            )

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            HomeUiState()
        )

    init {
        loadAudioFiles()
        startPlaybackServiceAndBind()
        startProgressUpdater()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startPlaybackServiceAndBind() {
        val intent = Intent(context, PlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
        bindToPlaybackService()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindToPlaybackService() {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        viewModelScope.launch {
            try {
                val controller = MediaController.Builder(context, sessionToken).buildAsync().await()
                mediaController = controller
                controller.addListener(playerListener)
                syncPlayerState()
            } catch (e: Exception) {
                e.printStackTrace()
                delay(1000)
                bindToPlaybackService()
            }
        }
    }

    private fun syncPlayerState() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        val currentMediaItem = controller.currentMediaItem
        if (currentMediaItem != null) {
            val uri = currentMediaItem.localConfiguration?.uri.toString()
            val track = _tracks.value.find { it.mediaUri == uri }
            if (track != null) _currentTrack.value = track
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncPlayerState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                handlePlaybackEnd()
            }
        }
    }

    private fun handlePlaybackEnd() {
        when (_repeatMode.value) {
            RepeatMode.REPEAT_ONE -> {
                _currentTrack.value?.let { track ->
                    val mediaItem = MediaItem.Builder()
                        .setUri(track.mediaUri)
                        .setMediaId(track.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .build()
                        )
                        .build()
                    mediaController?.setMediaItem(mediaItem)
                    mediaController?.prepare()
                    mediaController?.play()
                }
            }

            RepeatMode.REPEAT_ALL -> {
                val current = _currentTrack.value
                val list = filteredTracks.value
                if (list.isNotEmpty()) {
                    val index = current?.let { list.indexOf(it) } ?: -1
                    val nextIndex = if (index + 1 < list.size) index + 1 else 0
                    onTrackClick(list[nextIndex])
                }
            }

            RepeatMode.OFF -> {}
        }
    }

    private fun startProgressUpdater() {
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val duration = controller.duration
                    if (duration > 0) {
                        _playbackProgress.value = controller.currentPosition.toFloat() / duration
                    }
                }
            }
        }
    }

    fun loadAudioFiles() {
        viewModelScope.launch {
            _tracks.value = getAudioTracksUseCase()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSortType(sortType: SortType) {
        _sortType.value = sortType
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
    }

    fun onTrackClick(track: AudioTrack) {
        _currentTrack.value = track
        val mediaItem = MediaItem.Builder()
            .setUri(track.mediaUri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()

        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()
    }

    fun togglePlayPause() {
        if (mediaController?.isPlaying == true) mediaController?.pause()
        else mediaController?.play()
    }

    fun nextTrack() {
        val current = _currentTrack.value ?: return
        val list = filteredTracks.value
        val index = list.indexOf(current)
        if (index in 0 until list.size - 1) {
            onTrackClick(list[index + 1])
        } else if (_repeatMode.value == RepeatMode.REPEAT_ALL && list.isNotEmpty()) {
            onTrackClick(list[0])
        }
    }

    fun previousTrack() {
        val current = _currentTrack.value ?: return
        val list = filteredTracks.value
        val index = list.indexOf(current)
        if (index > 0) {
            onTrackClick(list[index - 1])
        } else if (_repeatMode.value == RepeatMode.REPEAT_ALL && list.isNotEmpty()) {
            onTrackClick(list.last())
        }
    }

    fun seekTo(progress: Float) {
        val duration = mediaController?.duration ?: 0
        if (duration > 0) {
            mediaController?.seekTo((progress * duration).toLong())
            _playbackProgress.value = progress
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }


    fun deleteTrack(track: AudioTrack) {
        pendingDeleteTrack = track

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    deleteModern(track)
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    deleteScopedStorage(track)
                }
                else -> {
                    deleteLegacy(track)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioViewModel", "Delete failed", e)
        }
    }

    private fun deleteLegacy(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(track.path)

                if (file.exists()) {
                    file.delete()
                }
                context.contentResolver.delete(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Audio.Media._ID}=?",
                    arrayOf(track.id)
                )

                withContext(Dispatchers.Main) {
                    if (_currentTrack.value?.id == track.id) {
                        mediaController?.pause()
                        _currentTrack.value = null
                        _isPlaying.value = false
                    }
                    loadAudioFiles()
                    pendingDeleteTrack = null
                }

                Log.d("AudioViewModel", "Deleted successfully (Legacy)")

            } catch (e: Exception) {
                Log.e("AudioViewModel", "deleteLegacy()", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteScopedStorage(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id.toLong()
                )

                val deleted = context.contentResolver.delete(uri, null, null)

                if (deleted > 0) {
                    withContext(Dispatchers.Main) {
                        if (_currentTrack.value?.id == track.id) {
                            mediaController?.pause()
                            _currentTrack.value = null
                            _isPlaying.value = false
                        }
                        loadAudioFiles()
                        pendingDeleteTrack = null
                    }
                    Log.d("AudioViewModel", "Track deleted (Q)")
                }

            } catch (e: RecoverableSecurityException) {
                try {
                    pendingDeleteTrack = track
                    val intentSender = e.userAction.actionIntent.intentSender
                    val request = IntentSenderRequest.Builder(intentSender).build()

                    withContext(Dispatchers.Main) {
                        deleteRequestLauncher.launch(request)
                    }
                } catch (ex: Exception) {
                    Log.e("AudioViewModel", "Permission request failed", ex)
                }
            } catch (e: Exception) {
                Log.e("AudioViewModel", "deleteScopedStorage()", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun deleteModern(track: AudioTrack) {
        try {
            pendingDeleteTrack = track

            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.id.toLong()
            )

            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(uri)
            )

            val request = IntentSenderRequest.Builder(
                pendingIntent.intentSender
            ).build()

            deleteRequestLauncher.launch(request)

        } catch (e: Exception) {
            Log.e("AudioViewModel", "deleteModern()", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onDeleteSuccess() {
        pendingDeleteTrack?.let { track ->
            viewModelScope.launch(Dispatchers.IO) {

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    try {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            track.id.toLong()
                        )
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        Log.e("AudioViewModel", "Failed to delete track on Android 10 after permission granted", e)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    delay(100)
                }

                withContext(Dispatchers.Main) {
                    if (_currentTrack.value?.id == track.id) {
                        mediaController?.pause()
                        _currentTrack.value = null
                        _isPlaying.value = false
                    }


                    loadAudioFiles()

                    pendingDeleteTrack = null
                }
            }
        }
    }

    fun onDeleteCancel() {
        pendingDeleteTrack = null
    }

    fun shareTrack(track: AudioTrack) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                track.id.toLong()
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(shareIntent, "sharing track")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Log.e("AudioViewModel", "Error sharing track: ${e.message}", e)
        }
    }

}


