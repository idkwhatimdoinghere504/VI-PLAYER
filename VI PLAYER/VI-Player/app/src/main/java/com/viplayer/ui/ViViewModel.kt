package com.viplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viplayer.data.AppSettings
import com.viplayer.data.Library
import com.viplayer.data.MusicRepository
import com.viplayer.data.Playlist
import com.viplayer.data.PlaylistStore
import com.viplayer.data.SettingsStore
import com.viplayer.data.Song
import com.viplayer.data.SongSort
import com.viplayer.data.ThemeMode
import com.viplayer.playback.PlayerConnection
import com.viplayer.playback.PlayerUiState
import com.viplayer.playback.QueueItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Single source of truth for the whole app. */
class ViViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    private val settingsStore = SettingsStore(app)
    private val playlistStore = PlaylistStore(app)

    val player = PlayerConnection(app, viewModelScope)

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs.asStateFlow()

    private var songIndex: Map<Long, Song> = emptyMap()
    private var sleepJob: Job? = null

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val playlists: StateFlow<List<Playlist>> =
        playlistStore.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playerState: StateFlow<PlayerUiState> = player.state
    val queue: StateFlow<List<QueueItem>> = player.queue

    init {
        player.connect()
    }

    /* ------------------------------------------------------------ library */

    fun refreshLibrary() {
        viewModelScope.launch {
            val loaded = repository.load()
            songIndex = loaded.songs.associateBy { it.id }
            _library.value = loaded
        }
    }

    /** Songs ordered according to the current sort preference. */
    fun sortedSongs(): List<Song> = when (settings.value.sort) {
        SongSort.TITLE -> _library.value.songs.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        SongSort.ARTIST -> _library.value.songs.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
                .thenBy { it.trackNumber }
        )
        SongSort.ALBUM -> _library.value.songs.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.album }
                .thenBy { it.trackNumber }
        )
        SongSort.DATE_ADDED -> _library.value.songs.sortedByDescending { it.dateAdded }
        SongSort.DURATION -> _library.value.songs.sortedByDescending { it.durationMs }
    }

    fun songsInAlbum(albumId: Long): List<Song> =
        _library.value.songs.filter { it.albumId == albumId }
            .sortedBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }

    fun songsByArtist(name: String): List<Song> =
        _library.value.songs.filter { it.artist.equals(name, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.trackNumber })

    fun songsInPlaylist(playlist: Playlist): List<Song> = playlist.songIds.mapNotNull { songIndex[it] }

    fun searchResults(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        return _library.value.songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }).take(300)
    }

    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
    }

    fun albumArtUri(albumId: Long) = repository.albumArtUri(albumId)

    /* ------------------------------------------------------------ settings */

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setAccentIndex(index: Int) = viewModelScope.launch { settingsStore.setAccentIndex(index) }
    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }

    fun setSort(sort: SongSort) = viewModelScope.launch { settingsStore.setSort(sort) }

    /* ------------------------------------------------------------ playlists */

    fun createPlaylist(name: String, onCreated: (Playlist) -> Unit = {}) {
        viewModelScope.launch { onCreated(playlistStore.create(name)) }
    }

    fun renamePlaylist(id: String, name: String) =
        viewModelScope.launch { playlistStore.rename(id, name) }

    fun deletePlaylist(id: String) = viewModelScope.launch { playlistStore.delete(id) }

    fun addToPlaylist(playlistId: String, songIds: List<Long>) =
        viewModelScope.launch { playlistStore.addSongs(playlistId, songIds) }

    fun removeFromPlaylist(playlistId: String, songId: Long) =
        viewModelScope.launch { playlistStore.removeSong(playlistId, songId) }

    /* ------------------------------------------------------------ playback */

    fun play(song: Song, queue: List<Song>) = player.playSong(song, queue)

    fun playAll(songs: List<Song>, startIndex: Int = 0) = player.playQueue(songs, startIndex)

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        if (!playerState.value.shuffle) player.toggleShuffle()
        player.playQueue(songs, (songs.indices).random())
    }

    /* ------------------------------------------------------------ sleep timer */

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepRemainingMs.value = 0L
            return
        }
        sleepJob = viewModelScope.launch {
            var remaining = minutes * 60_000L
            while (remaining > 0L) {
                _sleepRemainingMs.value = remaining
                delay(1_000L)
                remaining -= 1_000L
            }
            _sleepRemainingMs.value = 0L
            if (player.state.value.isPlaying) player.togglePlayPause()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = 0L
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
