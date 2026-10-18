package com.viplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.viplayer.data.Playlist
import com.viplayer.data.Song
import com.viplayer.playback.EqualizerController
import com.viplayer.ui.components.MiniPlayer
import com.viplayer.ui.components.SongActionSheet
import com.viplayer.ui.screens.AlbumDetailScreen
import com.viplayer.ui.screens.AlbumsScreen
import com.viplayer.ui.screens.ArtistsScreen
import com.viplayer.ui.screens.EqualizerScreen
import com.viplayer.ui.screens.NameInputDialog
import com.viplayer.ui.screens.NowPlayingScreen
import com.viplayer.ui.screens.PlaylistDetailScreen
import com.viplayer.ui.screens.PlaylistsScreen
import com.viplayer.ui.screens.SearchScreen
import com.viplayer.ui.screens.SettingsScreen
import com.viplayer.ui.screens.SongsScreen
import com.viplayer.ui.theme.ViTheme

private const val ROUTE_SONGS = "songs"
private const val ROUTE_ALBUMS = "albums"
private const val ROUTE_ARTISTS = "artists"
private const val ROUTE_PLAYLISTS = "playlists"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ALBUM_DETAIL = "album/{albumId}"
private const val ROUTE_ARTIST_DETAIL = "artist/{artistName}"
private const val ROUTE_PLAYLIST_DETAIL = "playlist/{playlistId}"
private const val ROUTE_NOW_PLAYING = "nowplaying"
private const val ROUTE_EQUALIZER = "equalizer"
private const val ROUTE_SEARCH = "search"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(ROUTE_SONGS, "Songs", Icons.Filled.LibraryMusic),
    Tab(ROUTE_ALBUMS, "Albums", Icons.Filled.Album),
    Tab(ROUTE_ARTISTS, "Artists", Icons.Filled.Person),
    Tab(ROUTE_PLAYLISTS, "Playlists", Icons.Filled.PlaylistPlay),
    Tab(ROUTE_SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun ViApp(viewModel: ViViewModel) {
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepRemainingMs.collectAsStateWithLifecycle()
    val eqState by EqualizerController.state.collectAsStateWithLifecycle()

    /* ------------------------------------------------------ permission */

    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[audioPermission] == true
        if (hasPermission) viewModel.refreshLibrary()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val wanted = mutableListOf(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                wanted += Manifest.permission.POST_NOTIFICATIONS
            }
            permissionLauncher.launch(wanted.toTypedArray())
        } else {
            viewModel.refreshLibrary()
        }
    }

    /* ------------------------------------------------------ ui state */

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var actionSong by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var createWithSong by remember { mutableStateOf<Song?>(null) }
    var renamePlaylist by remember { mutableStateOf<Playlist?>(null) }

    fun artFor(albumId: Long): Any? =
        if (albumId > 0L) viewModel.albumArtUri(albumId) else null

    fun play(song: Song, songs: List<Song>) = viewModel.play(song, songs)

    val bottomBarHeight = 64.dp
    val contentPadding = PaddingValues(bottom = bottomBarHeight + 8.dp)

    val showChrome = currentRoute in Tabs.map { it.route }

    ViTheme(
        themeMode = settings.themeMode,
        accentIndex = settings.accentIndex,
        dynamicColor = settings.dynamicColor
    ) {
        Scaffold(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showChrome) {
                    Column {
                        if (playerState.currentSongId > 0L) {
                            MiniPlayer(
                                state = playerState,
                                artModel = artFor(playerState.albumId),
                                onExpand = { navController.navigate(ROUTE_NOW_PLAYING) },
                                onTogglePlay = { viewModel.player.togglePlayPause() },
                                onNext = { viewModel.player.next() }
                            )
                        }
                        NavigationBar {
                            Tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(ROUTE_SONGS) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                }
            }
        ) { scaffoldPadding ->
            val topPadding = scaffoldPadding.calculateTopPadding()
            NavHost(
                navController = navController,
                startDestination = ROUTE_SONGS,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
            ) {
                composable(ROUTE_SONGS) {
                    Column(Modifier.fillMaxSize()) {
                        LibraryTopBar(
                            title = "Songs",
                            onSearch = { navController.navigate(ROUTE_SEARCH) }
                        )
                        SongsScreen(
                            songs = viewModel.sortedSongs(),
                            currentSongId = playerState.currentSongId,
                            artModel = { artFor(it) },
                            contentPadding = contentPadding,
                            onPlay = { play(it, viewModel.sortedSongs()) },
                            onShuffle = { viewModel.shuffleAll(viewModel.sortedSongs()) },
                            onLongPress = { actionSong = it }
                        )
                    }
                }

                composable(ROUTE_ALBUMS) {
                    Column(Modifier.fillMaxSize()) {
                        LibraryTopBar(
                            title = "Albums",
                            onSearch = { navController.navigate(ROUTE_SEARCH) }
                        )
                        AlbumsScreen(
                            albums = library.albums,
                            artModel = { artFor(it) },
                            contentPadding = contentPadding,
                            onOpen = { navController.navigate("album/${it.id}") }
                        )
                    }
                }

                composable(ROUTE_ARTISTS) {
                    Column(Modifier.fillMaxSize()) {
                        LibraryTopBar(
                            title = "Artists",
                            onSearch = { navController.navigate(ROUTE_SEARCH) }
                        )
                        ArtistsScreen(
                            artists = library.artists,
                            contentPadding = contentPadding,
                            onOpen = { navController.navigate("artist/${it.name.encodeArg()}") }
                        )
                    }
                }

                composable(ROUTE_PLAYLISTS) {
                    Column(Modifier.fillMaxSize()) {
                        LibraryTopBar(
                            title = "Playlists",
                            onSearch = { navController.navigate(ROUTE_SEARCH) }
                        )
                        PlaylistsScreen(
                            playlists = playlists,
                            contentPadding = contentPadding,
                            onOpen = { navController.navigate("playlist/${it.id}") },
                            onCreate = { showCreatePlaylist = true }
                        )
                    }
                }

                composable(ROUTE_SETTINGS) {
                    Column(Modifier.fillMaxSize()) {
                        LibraryTopBar(
                            title = "Settings",
                            onSearch = { navController.navigate(ROUTE_SEARCH) }
                        )
                        SettingsScreen(
                            settings = settings,
                            contentPadding = contentPadding,
                            sleepRemainingMs = sleepRemaining,
                            onThemeMode = viewModel::setThemeMode,
                            onAccent = viewModel::setAccentIndex,
                            onDynamicColor = viewModel::setDynamicColor,
                            onSort = viewModel::setSort,
                            onSleepTimer = viewModel::startSleepTimer
                        )
                    }
                }

                composable(
                    route = ROUTE_ALBUM_DETAIL,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: -1L
                    val album = library.albums.firstOrNull { it.id == albumId }
                    val songs = viewModel.songsInAlbum(albumId)
                    if (album == null) {
                        BackOnlyTopBar(title = "Album", onBack = { navController.popBackStack() })
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            BackOnlyTopBar(title = album.title, onBack = { navController.popBackStack() })
                            AlbumDetailScreen(
                                album = album,
                                songs = songs,
                                currentSongId = playerState.currentSongId,
                                artModel = { artFor(it) },
                                contentPadding = contentPadding,
                                onPlay = { play(it, songs) },
                                onPlayAlbum = { viewModel.playAll(songs) },
                                onShuffle = { viewModel.shuffleAll(songs) },
                                onLongPress = { actionSong = it }
                            )
                        }
                    }
                }

                composable(
                    route = ROUTE_ARTIST_DETAIL,
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType })
                ) { entry ->
                    val name = entry.arguments?.getString("artistName").orEmpty().decodeArg()
                    val songs = viewModel.songsByArtist(name)
                    Column(Modifier.fillMaxSize()) {
                        BackOnlyTopBar(title = name, onBack = { navController.popBackStack() })
                        SongsScreen(
                            songs = songs,
                            currentSongId = playerState.currentSongId,
                            artModel = { artFor(it) },
                            contentPadding = contentPadding,
                            onPlay = { play(it, songs) },
                            onShuffle = { viewModel.shuffleAll(songs) },
                            onLongPress = { actionSong = it }
                        )
                    }
                }

                composable(
                    route = ROUTE_PLAYLIST_DETAIL,
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("playlistId").orEmpty()
                    val playlist = playlists.firstOrNull { it.id == id }
                    if (playlist == null) {
                        BackOnlyTopBar(title = "Playlist", onBack = { navController.popBackStack() })
                    } else {
                        val songs = viewModel.songsInPlaylist(playlist)
                        Column(Modifier.fillMaxSize()) {
                            BackOnlyTopBar(
                                title = playlist.name,
                                onBack = { navController.popBackStack() }
                            )
                            PlaylistDetailScreen(
                                playlist = playlist,
                                songs = songs,
                                currentSongId = playerState.currentSongId,
                                artModel = { artFor(it) },
                                contentPadding = contentPadding,
                                onPlay = { play(it, songs) },
                                onPlayAll = { viewModel.playAll(songs) },
                                onShuffle = { viewModel.shuffleAll(songs) },
                                onRemoveSong = { viewModel.removeFromPlaylist(playlist.id, it.id) },
                                onDeletePlaylist = {
                                    viewModel.deletePlaylist(playlist.id)
                                    navController.popBackStack()
                                },
                                onRename = { renamePlaylist = playlist }
                            )
                        }
                    }
                }

                composable(ROUTE_NOW_PLAYING) {
                    var queueVisible by remember { mutableStateOf(false) }
                    NowPlayingScreen(
                        state = playerState,
                        artModel = artFor(playerState.albumId),
                        onBack = { navController.popBackStack() },
                        onTogglePlay = { viewModel.player.togglePlayPause() },
                        onNext = { viewModel.player.next() },
                        onPrevious = { viewModel.player.previous() },
                        onSeek = { viewModel.player.seekTo(it) },
                        onToggleShuffle = { viewModel.player.toggleShuffle() },
                        onCycleRepeat = { viewModel.player.cycleRepeat() },
                        onOpenQueue = { queueVisible = true },
                        onOpenEqualizer = { navController.navigate(ROUTE_EQUALIZER) },
                        queue = queue,
                        queueVisible = queueVisible,
                        onDismissQueue = { queueVisible = false },
                        onSelectQueueIndex = {
                            viewModel.player.seekToIndex(it)
                            queueVisible = false
                        }
                    )
                }

                composable(ROUTE_EQUALIZER) {
                    Column(Modifier.fillMaxSize()) {
                        BackOnlyTopBar(title = "Equalizer", onBack = { navController.popBackStack() })
                        EqualizerScreen(
                            state = eqState,
                            contentPadding = contentPadding,
                            onToggle = { EqualizerController.setEnabled(it) },
                            onBandLevel = { band, level ->
                                EqualizerController.setBandLevel(band, level)
                            },
                            onPreset = { EqualizerController.usePreset(it) },
                            onFlatten = { EqualizerController.flatten() }
                        )
                    }
                }

                composable(ROUTE_SEARCH) {
                    SearchScreen(
                        query = searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        results = viewModel.searchResults(searchQuery),
                        currentSongId = playerState.currentSongId,
                        artModel = { artFor(it) },
                        contentPadding = contentPadding,
                        onPlay = { play(it, viewModel.searchResults(searchQuery)) },
                        onLongPress = { actionSong = it },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        /* -------------------------------------------------- overlays */

        actionSong?.let { song ->
            SongActionSheet(
                song = song,
                playlists = playlists,
                onDismiss = { actionSong = null },
                onPlayNext = { viewModel.player.playNext(it); actionSong = null },
                onAddToQueue = { viewModel.player.addToQueue(listOf(it)); actionSong = null },
                onAddToPlaylist = { s, p ->
                    viewModel.addToPlaylist(p.id, listOf(s.id))
                    actionSong = null
                },
                onCreatePlaylist = {
                    createWithSong = song
                    actionSong = null
                    showCreatePlaylist = true
                }
            )
        }

        if (showCreatePlaylist) {
            NameInputDialog(
                title = "New playlist",
                confirmLabel = "Create",
                onDismiss = {
                    showCreatePlaylist = false
                    createWithSong = null
                },
                onConfirm = { name ->
                    val pending = createWithSong
                    viewModel.createPlaylist(name) { created ->
                        if (pending != null) {
                            viewModel.addToPlaylist(created.id, listOf(pending.id))
                        }
                    }
                    showCreatePlaylist = false
                    createWithSong = null
                }
            )
        }

        renamePlaylist?.let { playlist ->
            NameInputDialog(
                title = "Rename playlist",
                initial = playlist.name,
                onDismiss = { renamePlaylist = null },
                onConfirm = { name ->
                    viewModel.renamePlaylist(playlist.id, name)
                    renamePlaylist = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(title: String, onSearch: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackOnlyTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
        )
    )
}

/**
 * Artist names can contain characters that are meaningful in a navigation
 * route (notably "/"), so they travel as URL-safe Base64 rather than raw text.
 */
private fun String.encodeArg(): String =
    android.util.Base64.encodeToString(
        toByteArray(Charsets.UTF_8),
        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
    )

private fun String.decodeArg(): String =
    runCatching {
        String(
            android.util.Base64.decode(
                this,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            ),
            Charsets.UTF_8
        )
    }.getOrDefault(this)

