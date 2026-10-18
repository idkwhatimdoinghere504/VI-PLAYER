package com.viplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viplayer.data.Album
import com.viplayer.data.Artist
import com.viplayer.data.Song
import com.viplayer.ui.components.AlbumArt
import com.viplayer.ui.components.EmptyState
import com.viplayer.ui.components.SongRow
import com.viplayer.ui.components.formatDuration

@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongId: Long,
    artModel: (Long) -> Any?,
    contentPadding: PaddingValues,
    onPlay: (Song) -> Unit,
    onShuffle: () -> Unit,
    onLongPress: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            title = "No songs found",
            subtitle = "VI Player scans audio files the system has indexed on this device.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val totalMs = songs.sumOf { it.durationMs }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "${songs.size} songs \u00b7 ${formatDuration(totalMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onPlay(songs.first()) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all")
                    }
                    FilledTonalButton(onClick = onShuffle) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
        }

        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                artModel = artModel(song.albumId),
                isCurrent = song.id == currentSongId,
                onClick = { onPlay(song) },
                onLongClick = { onLongPress(song) }
            )
        }

    }
}

@Composable
fun AlbumsScreen(
    albums: List<Album>,
    artModel: (Long) -> Any?,
    contentPadding: PaddingValues,
    onOpen: (Album) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyState(
            title = "No albums yet",
            subtitle = "Albums appear here once the library has been scanned.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .clickable { onOpen(album) }
                    .padding(horizontal = 4.dp)
            ) {
                AlbumArt(
                    model = artModel(album.id),
                    size = 160.dp,
                    corner = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.songCount} " + if (album.songCount == 1) "track" else "tracks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    album: Album,
    songs: List<Song>,
    currentSongId: Long,
    artModel: (Long) -> Any?,
    contentPadding: PaddingValues,
    onPlay: (Song) -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffle: () -> Unit,
    onLongPress: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlbumArt(model = artModel(album.id), size = 210.dp, corner = 16.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val yearPart = if (album.year > 0) " \u00b7 ${album.year}" else ""
                Text(
                    text = "${album.songCount} tracks$yearPart",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onPlayAlbum) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    FilledTonalButton(onClick = onShuffle) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                artModel = null,
                isCurrent = song.id == currentSongId,
                onClick = { onPlay(song) },
                onLongClick = { onLongPress(song) }
            )
        }

    }
}

@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    contentPadding: PaddingValues,
    onOpen: (Artist) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyState(
            title = "No artists yet",
            subtitle = "Artists are grouped from the songs in your library.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(artists, key = { it.id.toString() + it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(artist) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(4.dp).height(36.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${artist.songCount} songs \u00b7 ${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}
