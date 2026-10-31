package com.example.musicplayer.musicFeature.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.musicFeature.core.platform.storage.RecoverableDeleteException
import com.example.musicplayer.musicFeature.core.platform.storage.RecoverableRenameException
import com.example.musicplayer.musicFeature.domain.model.AudioTrack
import com.example.musicplayer.musicFeature.domain.model.RepeatMode
import com.example.musicplayer.musicFeature.domain.model.SortType
import com.example.musicplayer.musicFeature.domain.service.TrackListProcessor
import com.example.musicplayer.musicFeature.domain.usecase.ConnectPlaybackUseCase
import com.example.musicplayer.musicFeature.domain.usecase.DeleteAudioTracksUseCase
import com.example.musicplayer.musicFeature.domain.usecase.GetAudioTracksUseCase
import com.example.musicplayer.musicFeature.domain.usecase.NextTrackUseCase
import com.example.musicplayer.musicFeature.domain.usecase.ObserveLastPlayedTrackIdUseCase
import com.example.musicplayer.musicFeature.domain.usecase.ObservePlaybackPositionUseCase
import com.example.musicplayer.musicFeature.domain.usecase.ObservePlaybackStateUseCase
import com.example.musicplayer.musicFeature.domain.usecase.ObserveSortTypeUseCase
import com.example.musicplayer.musicFeature.domain.usecase.PlayTracksUseCase
import com.example.musicplayer.musicFeature.domain.usecase.PreviousTrackUseCase
import com.example.musicplayer.musicFeature.domain.usecase.PrunePlaybackQueueUseCase
import com.example.musicplayer.musicFeature.domain.usecase.RefreshAudioTracksUseCase
import com.example.musicplayer.musicFeature.domain.usecase.RenameAudioTrackUseCase
import com.example.musicplayer.musicFeature.domain.usecase.SeekToUseCase
import com.example.musicplayer.musicFeature.domain.usecase.SetRepeatModeUseCase
import com.example.musicplayer.musicFeature.domain.usecase.SetSortTypeUseCase
import com.example.musicplayer.musicFeature.domain.usecase.StopPlaybackUseCase
import com.example.musicplayer.musicFeature.domain.usecase.TogglePlayPauseUseCase
import com.example.musicplayer.musicFeature.presentation.model.HomeUiState
import com.example.musicplayer.musicFeature.presentation.model.MusicEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class MusicViewModel @Inject constructor(
    getAudioTracks: GetAudioTracksUseCase,
    observeSortType: ObserveSortTypeUseCase,
    private val refreshAudioTracks: RefreshAudioTracksUseCase,
    private val setSortType: SetSortTypeUseCase,
    private val connectPlayback: ConnectPlaybackUseCase,
    private val playTracks: PlayTracksUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val nextTrack: NextTrackUseCase,
    private val previousTrack: PreviousTrackUseCase,
    private val seekToUseCase: SeekToUseCase,
    private val setRepeatMode: SetRepeatModeUseCase,
    private val deleteAudioTracks: DeleteAudioTracksUseCase,
    private val renameAudioTrack: RenameAudioTrackUseCase,
    private val stopPlayback: StopPlaybackUseCase,
    private val prunePlaybackQueue: PrunePlaybackQueueUseCase,
    observePlaybackState: ObservePlaybackStateUseCase,
    observeLastPlayedTrackId: ObserveLastPlayedTrackIdUseCase,
    observePlaybackPositionUseCase: ObservePlaybackPositionUseCase
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectionMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val loading = MutableStateFlow(true)
    private val hasLoadedTrackSnapshot = MutableStateFlow(false)
    private val effectsInternal = MutableSharedFlow<MusicEffect>(extraBufferCapacity = 16)
    private var playbackConnectionJob: Job? = null

    val effects = effectsInternal.asSharedFlow()

    private val allTracks: StateFlow<List<AudioTrack>> = getAudioTracks()
        .catch { error ->
            loading.value = false
            effectsInternal.emit(MusicEffect.ShowMessage("Could not read the music library"))
            emit(emptyList())
        }
        .onEach {
            hasLoadedTrackSnapshot.value = true
            loading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val trackById: StateFlow<Map<Long, AudioTrack>> = allTracks
        .map { tracks -> tracks.associateBy(AudioTrack::id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val currentSort: StateFlow<SortType> = observeSortType()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortType.ALPHABETICAL)

    private val sortedTracks: StateFlow<List<AudioTrack>> = combine(allTracks, currentSort) { tracks, sortType ->
        TrackListProcessor.sort(tracks, sortType)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class)
    private val debouncedSearch = searchQuery
        .debounce(200.milliseconds)
        .distinctUntilChanged()

    private val visibleTracks: StateFlow<List<AudioTrack>> = combine(sortedTracks, debouncedSearch) { tracks, query ->
        TrackListProcessor.filter(tracks, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val visibleIndexById: StateFlow<Map<Long, Int>> = visibleTracks
        .map { tracks -> buildMap(tracks.size) { tracks.forEachIndexed { index, track -> put(track.id, index) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val playbackState = observePlaybackState()
    private val lastPlayedTrackId = observeLastPlayedTrackId()
    private val uiPlaybackState = combine(playbackState, lastPlayedTrackId) { playback, lastPlayedId ->
        if (playback.currentTrackId == null) playback.copy(currentTrackId = lastPlayedId) else playback
    }.stateIn(viewModelScope, SharingStarted.Eagerly, playbackState.value.copy(currentTrackId = playbackState.value.currentTrackId ?: lastPlayedTrackId.value))
    val playbackPosition = observePlaybackPositionUseCase()

    private val baseUiState = combine(
        visibleTracks,
        searchQuery,
        currentSort,
        uiPlaybackState,
        trackById
    ) { tracks, query, sort, playback, byId ->
        HomeUiState(
            tracks = tracks,
            searchQuery = query,
            currentTrack = playback.currentTrackId?.let(byId::get),
            isPlaying = playback.isPlaying,
            repeatMode = playback.repeatMode,
            sortType = sort
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseUiState,
        loading,
        selectionMode,
        selectedIds
    ) { base, isLoading, isSelection, selected ->
        base.copy(
            isLoading = isLoading,
            isSelectionMode = isSelection,
            selectedIds = selected
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private var pendingDeleteTracks: List<AudioTrack> = emptyList()
    private var retryAfterDeleteApproval = false
    private var deleteJob: Job? = null
    private data class PendingRename(val track: AudioTrack, val newName: String)
    private var pendingRename: PendingRename? = null

    init {
        ensurePlaybackConnected()
        viewModelScope.launch {
            allTracks.collect { tracks ->
                // Do not prune a live MediaSession queue using StateFlow's temporary initial [].
                if (!hasLoadedTrackSnapshot.value) return@collect

                val availableIds = tracks.mapTo(HashSet(tracks.size), AudioTrack::id)
                val currentTrackId = playbackState.value.currentTrackId
                if (currentTrackId != null && currentTrackId !in availableIds) {
                    runCatching { stopPlayback() }
                } else {
                    runCatching { prunePlaybackQueue(availableIds) }
                }
            }
        }
    }
    fun ensurePlaybackConnected() {
        if (playbackConnectionJob?.isActive == true) return
        playbackConnectionJob = viewModelScope.launch {
            runCatching { connectPlayback() }
                .onFailure { error ->
                    Log.w("MusicPlayback", "Could not reconnect to playback service", error)
                }
        }
    }

    fun refresh() {
        if (deleteJob?.isActive == true) return
        viewModelScope.launch {
            if (allTracks.value.isEmpty()) loading.value = true
            runCatching { refreshAudioTracks() }
                .onFailure {
                    loading.value = false
                    effectsInternal.emit(MusicEffect.ShowMessage("Could not refresh music library"))
                }
        }
    }

    fun onSearchQueryChanged(value: String) {
        searchQuery.value = value
    }

    fun chooseSortType(sortType: SortType) {
        if (sortType == currentSort.value) return
        viewModelScope.launch {
            runCatching { setSortType(sortType) }
                .onFailure { effectsInternal.emit(MusicEffect.ShowMessage("Could not save sort preference")) }
        }
    }

    fun onTrackClicked(track: AudioTrack) {
        if (selectionMode.value) {
            toggleSelection(track.id)
            return
        }

        val playlist = visibleTracks.value
        val index = playlist.indexOfFirst { it.id == track.id }
        if (index < 0) return

        viewModelScope.launch {
            runCatching {
                playTracks(playlist, index)
            }.onFailure { error ->
                Log.e(
                    "MusicPlayback",
                    "Failed to play track: id=${track.id}, uri=${track.mediaUri}",
                    error
                )
                effectsInternal.emit(
                    MusicEffect.ShowMessage("This audio file could not be played")
                )
            }
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.currentTrackId == null) {
            val lastId = lastPlayedTrackId.value ?: return
            val playlist = allTracks.value
            val index = playlist.indexOfFirst { it.id == lastId }
            if (index >= 0) {
                viewModelScope.launch {
                    runCatching { playTracks(playlist, index) }
                        .onFailure { effectsInternal.emit(MusicEffect.ShowMessage("This audio file could not be played")) }
                }
            }
        } else {
            launchPlayback { togglePlayPauseUseCase() }
        }
    }
    fun next() = launchPlayback { nextTrack() }
    fun previous() = launchPlayback { previousTrack() }
    fun seekTo(progress: Float) = launchPlayback { seekToUseCase(progress) }

    fun toggleRepeatMode() {
        val next = when (playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        launchPlayback { setRepeatMode(next) }
    }

    fun enterSelectionMode(trackId: Long) {
        selectionMode.value = true
        selectedIds.value = setOf(trackId)
    }

    fun toggleSelection(trackId: Long) {
        selectedIds.update { current ->
            if (trackId in current) current - trackId else current + trackId
        }
        if (selectedIds.value.isEmpty()) selectionMode.value = false
    }

    fun selectAllVisible() {
        val ids = visibleTracks.value.mapTo(LinkedHashSet()) { it.id }
        selectedIds.value = ids
        selectionMode.value = ids.isNotEmpty()
    }

    fun clearSelection() {
        selectionMode.value = false
        selectedIds.value = emptySet()
    }

    fun requestDelete(track: AudioTrack) = requestDelete(listOf(track))

    fun requestDeleteSelected() {
        val selected = selectedIds.value
        val tracks = allTracks.value.filter { it.id in selected }
        requestDelete(tracks)
    }

    private fun requestDelete(tracks: List<AudioTrack>) {
        if (tracks.isEmpty() || pendingDeleteTracks.isNotEmpty()) return
        deleteJob?.cancel()
        deleteJob = null
        pendingDeleteTracks = tracks.distinctBy(AudioTrack::id)
        retryAfterDeleteApproval = false
        deleteNextBatch()
    }

    private fun deleteNextBatch() {
        if (deleteJob?.isActive == true) return
        val batch = pendingDeleteTracks.take(2_000)
        if (batch.isEmpty()) {
            finishDelete()
            return
        }

        deleteJob = viewModelScope.launch {
            try {
                deleteAudioTracks(batch)
                pendingDeleteTracks = pendingDeleteTracks.drop(batch.size)
                deleteJob = null
                deleteNextBatch()
            } catch (e: RecoverableDeleteException) {
                deleteJob = null
                pendingDeleteTracks = pendingDeleteTracks.filterNot { it.id in e.completedTrackIds }
                retryAfterDeleteApproval = e.retryAfterApproval
                effectsInternal.emit(MusicEffect.LaunchDeleteConfirmation(e.request))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                deleteJob = null
                effectsInternal.emit(MusicEffect.ShowMessage("Could not delete all selected songs"))
                pendingDeleteTracks = emptyList()
                retryAfterDeleteApproval = false
            }
        }
    }

    fun onDeleteConfirmationResult(approved: Boolean) {
        if (!approved) {
            pendingDeleteTracks = emptyList()
            retryAfterDeleteApproval = false
            return
        }

        if (retryAfterDeleteApproval) {
            retryAfterDeleteApproval = false
            deleteNextBatch()
            return
        }

        val deletedBatchSize = minOf(2_000, pendingDeleteTracks.size)
        pendingDeleteTracks = pendingDeleteTracks.drop(deletedBatchSize)
        deleteNextBatch()
    }

    private fun finishDelete() {
        pendingDeleteTracks = emptyList()
        retryAfterDeleteApproval = false
        clearSelection()
        effectsInternal.tryEmit(MusicEffect.NavigateBackAfterDelete)
        refreshAudioTracksInBackground()
    }

    private fun refreshAudioTracksInBackground() {
        viewModelScope.launch { runCatching { refreshAudioTracks() } }
    }

    fun requestRename(track: AudioTrack, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) {
            effectsInternal.tryEmit(MusicEffect.ShowMessage("The file name cannot be empty"))
            return
        }
        if (pendingRename != null) return
        val pending = PendingRename(track, cleanName)
        pendingRename = pending
        performRename(pending, afterApproval = false)
    }

    fun onRenameConfirmationResult(approved: Boolean) {
        val pending = pendingRename ?: return
        if (!approved) {
            pendingRename = null
            return
        }
        performRename(pending, afterApproval = true)
    }

    private fun performRename(pending: PendingRename, afterApproval: Boolean) {
        viewModelScope.launch {
            try {
                renameAudioTrack(pending.track, pending.newName, afterApproval)
                if (pendingRename == pending) pendingRename = null
                effectsInternal.emit(MusicEffect.ShowMessage("Song renamed"))
                refreshAudioTracks()
            } catch (error: RecoverableRenameException) {
                effectsInternal.emit(MusicEffect.LaunchRenameConfirmation(error.request))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (pendingRename == pending) pendingRename = null
                effectsInternal.emit(MusicEffect.ShowMessage(error.message ?: "Could not rename this song"))
            }
        }
    }

    fun shareTrack(track: AudioTrack) {
        effectsInternal.tryEmit(MusicEffect.ShareAudio(listOf(track.mediaUri)))
    }

    fun shareSelected() {
        val selected = selectedIds.value
        val uris = allTracks.value.filter { it.id in selected }.map(AudioTrack::mediaUri)
        if (uris.isNotEmpty()) effectsInternal.tryEmit(MusicEffect.ShareAudio(uris))
        clearSelection()
    }

    private fun launchPlayback(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { effectsInternal.emit(MusicEffect.ShowMessage("Playback command failed")) }
        }
    }
}
