package com.viplayer.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.viplayer.data.RepeatMode
import com.viplayer.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val EXTRA_ALBUM_ID = "vi_album_id"

/** One entry in the up-next list. */
data class QueueItem(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val current: Boolean
)

/** Everything the player UI renders from. */
data class PlayerUiState(
    val ready: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSongId: Long = -1L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = -1L,
    val artworkUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueSize: Int = 0,
    val currentIndex: Int = -1
)

/** Bridges the UI to the MediaSession hosted by [PlaybackService]. */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var queueKey: Pair<Int, Int>? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState()
            pushQueue(force = false)
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c != null) {
                controller = c
                c.addListener(listener)
                pushState()
                pushQueue(force = true)
                startTicker()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        controller?.let {
            runCatching { it.removeListener(listener) }
            runCatching { it.release() }
        }
        controller = null
    }

    /* ------------------------------------------------------------ commands */

    fun playQueue(songs: List<Song>, startIndex: Int) = withController { c ->
        if (songs.isEmpty()) return@withController
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playSong(song: Song, queue: List<Song>) =
        playQueue(queue, queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0))

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { c ->
        // Restart the track if we're more than 3s in, otherwise step back.
        if (c.currentPosition > 3_000L) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }

    fun seekToIndex(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun setRepeatMode(mode: RepeatMode) = withController { it.repeatMode = mode.toPlayerMode() }

    fun cycleRepeat() = withController { c -> c.repeatMode = c.repeatMode.nextMode() }

    fun toggleShuffle() = withController { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }

    fun addToQueue(songs: List<Song>) = withController { c ->
        c.addMediaItems(songs.map { it.toMediaItem() })
        pushQueue(force = true)
    }

    fun playNext(song: Song) = withController { c ->
        val index = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(index, song.toMediaItem())
        pushQueue(force = true)
    }

    fun removeFromQueue(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.removeMediaItem(index)
            pushQueue(force = true)
        }
    }

    fun moveInQueue(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) {
            c.moveMediaItem(from, to)
            pushQueue(force = true)
        }
    }

    fun stopAndClear() = withController { c ->
        c.stop()
        c.clearMediaItems()
        pushQueue(force = true)
    }

    /* ------------------------------------------------------------ internals */

    private inline fun withController(block: (MediaController) -> Unit) {
        val c = controller ?: return
        runCatching { block(c) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                if (controller?.isPlaying == true) pushState()
                delay(500L)
            }
        }
    }

    private fun pushState() {
        val c = controller ?: return
        val item = runCatching { c.currentMediaItem }.getOrNull()
        val meta = item?.mediaMetadata
        val duration = runCatching { c.duration }.getOrDefault(C.TIME_UNSET)

        _state.value = PlayerUiState(
            ready = true,
            isPlaying = c.isPlaying,
            currentSongId = item?.mediaId?.toLongOrNull() ?: -1L,
            title = meta?.title?.toString().orEmpty(),
            artist = meta?.artist?.toString().orEmpty(),
            album = meta?.albumTitle?.toString().orEmpty(),
            albumId = meta?.extras?.getLong(EXTRA_ALBUM_ID, -1L) ?: -1L,
            artworkUri = meta?.artworkUri?.toString(),
            positionMs = runCatching { c.currentPosition }.getOrDefault(0L).coerceAtLeast(0L),
            durationMs = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration,
            bufferedMs = runCatching { c.bufferedPosition }.getOrDefault(0L),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode.toRepeatMode(),
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            queueSize = c.mediaItemCount,
            currentIndex = c.currentMediaItemIndex
        )
    }

    private fun pushQueue(force: Boolean) {
        val c = controller ?: return
        val key = c.mediaItemCount to c.currentMediaItemIndex
        if (!force && queueKey == key) return
        queueKey = key

        val items = ArrayList<QueueItem>(c.mediaItemCount)
        for (i in 0 until c.mediaItemCount) {
            val item = runCatching { c.getMediaItemAt(i) }.getOrNull() ?: continue
            val meta = item.mediaMetadata
            items += QueueItem(
                id = item.mediaId.toLongOrNull() ?: -1L,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                albumId = meta.extras?.getLong(EXTRA_ALBUM_ID, -1L) ?: -1L,
                current = i == c.currentMediaItemIndex
            )
        }
        _queue.value = items
    }
}

/* ---------------------------------------------------------------- helpers */

fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply { putLong(EXTRA_ALBUM_ID, albumId) }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(Uri.parse("content://media/external/audio/albumart/$albumId"))
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
        .setMediaMetadata(metadata)
        .build()
}

private fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}

private fun RepeatMode.toPlayerMode(): Int = when (this) {
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
}

private fun Int.nextMode(): Int = when (this) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
