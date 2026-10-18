package com.viplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.playlistData: DataStore<Preferences> by
    preferencesDataStore(name = "vi_playlists")

/** Persists user playlists as a single JSON blob in DataStore. */
class PlaylistStore(private val context: Context) {

    private val key = stringPreferencesKey("playlists_json")
    private val json = Json { ignoreUnknownKeys = true }

    val playlists: Flow<List<Playlist>> = context.playlistData.data.map { it.readPlaylists() }

    suspend fun create(name: String): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "New playlist" },
            createdAt = System.currentTimeMillis()
        )
        mutate { it + playlist }
        return playlist
    }

    suspend fun rename(id: String, name: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    suspend fun addSongs(id: String, songIds: List<Long>) = mutate { list ->
        list.map {
            if (it.id == id) it.copy(songIds = (it.songIds + songIds).distinct()) else it
        }
    }

    suspend fun removeSong(id: String, songId: Long) = mutate { list ->
        list.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it }
    }

    private suspend fun mutate(block: (List<Playlist>) -> List<Playlist>) {
        context.playlistData.edit { prefs ->
            prefs[key] = json.encodeToString(block(prefs.readPlaylists()))
        }
    }

    private fun Preferences.readPlaylists(): List<Playlist> {
        val raw = this[key] ?: return emptyList()
        return runCatching { json.decodeFromString<List<Playlist>>(raw) }.getOrDefault(emptyList())
    }
}
