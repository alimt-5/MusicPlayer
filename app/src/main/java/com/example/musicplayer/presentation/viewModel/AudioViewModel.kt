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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

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

    private val _filteredTracks =
        MutableStateFlow<List<AudioTrack>>(emptyList())
    private var mediaController: MediaController? = null

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()


    private var pendingDeleteTrack: AudioTrack? = null

    val uiState: StateFlow<HomeUiState> = combine(
        _tracks,
        _searchQuery,
        _sortType,
        _repeatMode,
        _currentTrack,
        _isPlaying,
    ) {
        val filtered = if (_searchQuery.value.isBlank()) _tracks.value else {
            _tracks.value.filter {
                it.title.contains(_searchQuery.value, ignoreCase = true) ||
                        it.artist.contains(_searchQuery.value, ignoreCase = true)
            }
        }
        val sorted = when (_sortType.value) {
            SortType.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
            SortType.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
        }
        _filteredTracks.value = sorted
        HomeUiState(
            sorted,
            _searchQuery.value,
            _currentTrack.value,
            _isPlaying.value,
            _sortType.value,
            _repeatMode.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
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
                val list = _filteredTracks.value
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
            while (true) {
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
        val list = _filteredTracks.value
        val index = list.indexOf(current)
        if (index in 0 until list.size - 1) {
            onTrackClick(list[index + 1])
        } else if (_repeatMode.value == RepeatMode.REPEAT_ALL && list.isNotEmpty()) {
            onTrackClick(list[0])
        }
    }

    fun previousTrack() {
        val current = _currentTrack.value ?: return
        val list = _filteredTracks.value
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




    @RequiresApi(Build.VERSION_CODES.Q)
    fun deleteTrack(track: AudioTrack) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pendingDeleteTrack = track
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id.toLong()
                )
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                )
                val intentSender = pendingIntent.intentSender
                val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
                deleteRequestLauncher.launch(intentSenderRequest)
                Log.d("AudioViewModel", "Delete request launched (Android 11+)")
            } else {
                Log.d("AudioViewModel", "Direct delete (Android 10 or lower)")
                performDelete(track)
            }
        } catch (e: Exception) {
            Log.e("AudioViewModel", "Error in deleteTrack", e)
            performDelete(track)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun performDelete(track: AudioTrack) {
        viewModelScope.launch {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id.toLong()
                )
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    Log.d("AudioViewModel", "Deleted: ${track.title}")
                    _tracks.value = _tracks.value.filter { it.id != track.id }
                    if (_currentTrack.value?.id == track.id) {
                        mediaController?.pause()
                        _currentTrack.value = null
                        _isPlaying.value = false
                    }
                } else {
                    Log.e("AudioViewModel", "Failed to delete: ${track.title}")
                }
            } catch (e: RecoverableSecurityException) {
                Log.w("AudioViewModel", "RecoverableSecurityException, requesting permission...", e)
                try {
                    val intentSender = e.userAction.actionIntent.intentSender
                    val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
                    pendingDeleteTrack = track
                    deleteRequestLauncher.launch(intentSenderRequest)
                } catch (ex: Exception) {
                    Log.e("AudioViewModel", "Error requesting permission", ex)
                }
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Error deleting track", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun onDeleteSuccess() {
        pendingDeleteTrack?.let {
            performDelete(it)
            pendingDeleteTrack = null
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


