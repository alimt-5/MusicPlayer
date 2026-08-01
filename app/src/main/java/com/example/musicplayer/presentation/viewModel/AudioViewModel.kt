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
    private var pendingDeleteTracks: List<AudioTrack> = emptyList()

    private val _isLoading = MutableStateFlow(false)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())


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
            _repeatMode,
            _isLoading,
            _isSelectionMode,
            _selectedIds
        ) {
            HomeUiState(
                tracks = filteredTracks.value,
                searchQuery = _searchQuery.value,
                currentTrack = _currentTrack.value,
                isPlaying = _isPlaying.value,
                sortType = _sortType.value,
                repeatMode = _repeatMode.value,
                isLoading = _isLoading.value,
                isSelectionMode = _isSelectionMode.value,
                selectedIds = _selectedIds.value
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

    fun refreshTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                delay(1000)
                _tracks.value = getAudioTracksUseCase()
                Log.e("AudioViewModel", "Refresh Done")
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Refresh failed", e)
            } finally {
                _isLoading.value = false
            }
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


    //Single Delete And Share

    fun deleteTrack(track: AudioTrack) {
        deleteTracks(listOf(track))
    }

    private fun deleteTracks(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        pendingDeleteTracks = tracks

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    deleteModern(tracks)
                }

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    deleteScopedStorage(tracks)
                }

                else -> {
                    deleteLegacy(tracks)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioViewModel", "Delete failed", e)
        }
    }

    //Android 10-
    private fun deleteLegacy(tracks: List<AudioTrack>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tracks.forEach { track ->
                    val file = File(track.path)
                    if (file.exists()) {
                        file.delete()
                    }
                    context.contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media._ID}=?",
                        arrayOf(track.id)
                    )
                }

                withContext(Dispatchers.Main) {
                    val deletedIds = tracks.map { it.id }.toSet()
                    _tracks.value = _tracks.value.filterNot { it.id in deletedIds }

                    if (_currentTrack.value?.id in deletedIds) {
                        mediaController?.pause()
                        _currentTrack.value = null
                        _isPlaying.value = false
                    }
                    exitSelectionMode()
                    loadAudioFiles()
                    pendingDeleteTracks = emptyList()
                }

            } catch (e: Exception) {
                Log.e("AudioViewModel", "deleteLegacy() error", e)
            }
        }
    }

    //Android10
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun processNextPendingDeleteQ() {
        val remaining = pendingDeleteTracks
        if (remaining.isEmpty()) {
            viewModelScope.launch(Dispatchers.Main) {
                exitSelectionMode()
                loadAudioFiles()
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val nextTrack = remaining.first()
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                nextTrack.id.toLong()
            )

            try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    pendingDeleteTracks = remaining.drop(1)

                    withContext(Dispatchers.Main) {
                        _tracks.value = _tracks.value.filterNot { it.id == nextTrack.id }
                        if (_currentTrack.value?.id == nextTrack.id) {
                            mediaController?.pause()
                            _currentTrack.value = null
                            _isPlaying.value = false
                        }
                    }

                    processNextPendingDeleteQ()
                }
            } catch (e: RecoverableSecurityException) {
                val intentSender = e.userAction.actionIntent.intentSender
                val request = IntentSenderRequest.Builder(intentSender).build()

                withContext(Dispatchers.Main) {
                    deleteRequestLauncher.launch(request)
                }
            } catch (e: Exception) {
                Log.e("AudioViewModel", "Failed to delete track ${nextTrack.id}", e)
                pendingDeleteTracks = remaining.drop(1)
                processNextPendingDeleteQ()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteScopedStorage(tracks: List<AudioTrack>) {
        pendingDeleteTracks = tracks
        processNextPendingDeleteQ()
    }

    //android 11+
    @RequiresApi(Build.VERSION_CODES.R)
    private fun deleteModern(tracks: List<AudioTrack>) {
        try {
            pendingDeleteTracks = tracks

            val uris = tracks.map { track ->
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id.toLong()
                )
            }

            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                uris
            )

            val request = IntentSenderRequest.Builder(
                pendingIntent.intentSender
            ).build()

            deleteRequestLauncher.launch(request)

        } catch (e: Exception) {
            Log.e("AudioViewModel", "deleteModern() error", e)
        }
    }

    fun onDeleteSuccess() {
        if (pendingDeleteTracks.isEmpty()) return

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // در اندروید ۱۰، فایل جاری تایید شده و حذف می‌شود، سپس فایل بعدی پردازش خواهد شد
            val currentTrack = pendingDeleteTracks.firstOrNull()
            if (currentTrack != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            currentTrack.id.toLong()
                        )
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        Log.e("AudioViewModel", "Q retry delete failed", e)
                    }

                    withContext(Dispatchers.Main) {
                        _tracks.value = _tracks.value.filterNot { it.id == currentTrack.id }
                        if (_currentTrack.value?.id == currentTrack.id) {
                            mediaController?.pause()
                            _currentTrack.value = null
                            _isPlaying.value = false
                        }

                        // رفتن به سراغ آهنگ بعدی در صف
                        pendingDeleteTracks = pendingDeleteTracks.drop(1)
                        processNextPendingDeleteQ()
                    }
                }
            }
        } else {
            // در اندروید ۱۱ به بالا (R+) همه یک‌جا حذف شده‌اند
            viewModelScope.launch(Dispatchers.IO) {
                delay(200)
                withContext(Dispatchers.Main) {
                    val deletedIds = pendingDeleteTracks.map { it.id }.toSet()
                    _tracks.value = _tracks.value.filterNot { deletedIds.contains(it.id) }

                    if (_currentTrack.value?.id in deletedIds) {
                        mediaController?.pause()
                        _currentTrack.value = null
                        _isPlaying.value = false
                    }

                    exitSelectionMode()
                    loadAudioFiles()
                    pendingDeleteTracks = emptyList()
                }
            }
        }
    }

    fun onDeleteCancel() {
        pendingDeleteTracks = emptyList()
    }

    //Share Track
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


    // Multiple Delete And Share
    fun toggleSelection(trackId: String) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(trackId)) {
            current - trackId
        } else {
            current + trackId
        }
        if (_selectedIds.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun enterSelectionMode(trackId: String? = null) {
        _isSelectionMode.value = true
        trackId?.let {
            _selectedIds.value = setOf(it)
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun selectAll() {
        val allIds = filteredTracks.value.map { it.id }.toSet()
        _selectedIds.value = allIds
        _isSelectionMode.value = true
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return

        val tracksToDelete = _tracks.value.filter { ids.contains(it.id) }
        deleteTracks(tracksToDelete)
    }

    //Multiple Share
    fun shareSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return

        val tracksToShare = _tracks.value.filter { ids.contains(it.id) }
        if (tracksToShare.isEmpty()) return

        try {
            val uris = tracksToShare.map { track ->
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    track.id.toLong()
                )
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(shareIntent, "Sharing Songs")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
            exitSelectionMode()
        } catch (e: Exception) {
            Log.e("AudioViewModel", "Error sharing selected tracks: ${e.message}", e)
        }
    }

}
