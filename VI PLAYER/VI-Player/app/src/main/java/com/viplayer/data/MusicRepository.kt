package com.viplayer.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device audio library through MediaStore.
 *
 * Column look-ups go through [longOr]/[stringOr] rather than
 * `getColumnIndexOrThrow` because a few useful columns (artist_id, album_id)
 * are not guaranteed to be exposed by every OEM MediaStore implementation.
 */
class MusicRepository(private val context: Context) {

    fun songUri(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /** Best-effort album artwork handle. Callers must tolerate failure. */
    fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

    suspend fun load(): Library = withContext(Dispatchers.IO) {
        val songs = querySongs()
        Library(
            songs = songs,
            albums = buildAlbums(songs),
            artists = buildArtists(songs),
            loaded = true
        )
    }

    private fun querySongs(): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val out = ArrayList<Song>()
        val cursor: Cursor? = runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                order
            )
        }.getOrNull()

        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.longOr(MediaStore.Audio.Media._ID, -1L)
                if (id < 0L) continue

                val title = c.stringOr(MediaStore.Audio.Media.TITLE, "Unknown title")
                val artist = c.stringOr(MediaStore.Audio.Media.ARTIST, "Unknown artist")
                    .let { if (it == MediaStore.UNKNOWN_STRING) "Unknown artist" else it }
                val album = c.stringOr(MediaStore.Audio.Media.ALBUM, "Unknown album")
                    .let { if (it == MediaStore.UNKNOWN_STRING) "Unknown album" else it }

                out += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    artistId = c.longOr(ARTIST_ID, stableId(artist)),
                    album = album,
                    albumId = c.longOr(ALBUM_ID, stableId(album)),
                    durationMs = c.longOr(MediaStore.Audio.Media.DURATION, 0L),
                    trackNumber = c.longOr(MediaStore.Audio.Media.TRACK, 0L).toInt(),
                    year = c.longOr(MediaStore.Audio.Media.YEAR, 0L).toInt(),
                    path = c.stringOr(MediaStore.Audio.Media.DATA, "").ifEmpty { null },
                    dateAdded = c.longOr(MediaStore.Audio.Media.DATE_ADDED, 0L),
                    sizeBytes = c.longOr(MediaStore.Audio.Media.SIZE, 0L)
                )
            }
        }
        return out
    }

    private fun buildAlbums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (albumId, group) ->
                val first = group.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = group.map { it.artist }.distinct().joinToString(", ").ifEmpty { first.artist },
                    songCount = group.size,
                    year = group.maxOfOrNull { it.year } ?: 0
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    private fun buildArtists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }
            .map { (name, group) ->
                Artist(
                    id = group.first().artistId,
                    name = name,
                    songCount = group.size,
                    albumCount = group.map { it.albumId }.distinct().size
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** Deterministic fallback id when MediaStore does not supply one. */
    private fun stableId(seed: String): Long = seed.lowercase().hashCode().toLong()

    private fun Cursor.longOr(name: String, fallback: Long): Long {
        val i = getColumnIndex(name)
        return if (i >= 0 && !isNull(i)) getLong(i) else fallback
    }

    private fun Cursor.stringOr(name: String, fallback: String): String {
        val i = getColumnIndex(name)
        return if (i >= 0 && !isNull(i)) (getString(i) ?: fallback) else fallback
    }

    companion object {
        private const val ARTIST_ID = "artist_id"
        private const val ALBUM_ID = "album_id"
        private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
