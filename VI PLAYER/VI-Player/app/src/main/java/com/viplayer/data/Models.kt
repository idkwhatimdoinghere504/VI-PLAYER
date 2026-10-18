package com.viplayer.data

import kotlinx.serialization.Serializable

/** A single playable audio track, sourced from MediaStore. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val path: String?,
    val dateAdded: Long,
    val sizeBytes: Long
)

/** An album grouping produced from the song library. */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int
)

/** An artist grouping produced from the song library. */
data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

/** A user-created playlist. Only song ids are stored; titles are resolved live. */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = 0L
)

enum class RepeatMode { OFF, ALL, ONE }

enum class SongSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Recently added"),
    DURATION("Duration")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Snapshot of everything the player UI needs to render itself. */
data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val loaded: Boolean = false
)
