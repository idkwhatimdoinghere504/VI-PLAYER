# VI Player

A local music player for Android, written in Kotlin with Jetpack Compose and
Media3. It reads the audio the system has already indexed and plays it locally —
nothing is uploaded, and there is no network code anywhere in the app.

This is an original implementation. It is not derived from, and shares no code
or assets with, any other music player.

---

## Features

**Library**
- Songs, albums and artists grouped from `MediaStore`
- Search across song, artist and album names
- Sort by title, artist, album, recently added or duration
- Play all / shuffle from any list, album or artist view

**Playback**
- Media3 `ExoPlayer` behind a `MediaSessionService`, so audio keeps playing
  when the app is backgrounded and responds to system media controls
- Lock-screen / notification controls provided by the MediaSession
- Play / pause, next, previous (restarts the track if >3s in)
- Shuffle and repeat off → all → one
- Queue view with tap-to-jump
- Audio focus handling, pause when headphones are unplugged

**Playlists**
- Create, rename and delete playlists
- Add songs from the long-press action sheet
- Play or shuffle a playlist

**Extras**
- 5-band system equalizer with presets, driven by `android.media.audiofx.Equalizer`
- Sleep timer with a live countdown
- Six accent colours, light / dark / system theme, optional Material You
  dynamic colour on Android 12+

## Requirements

| Tool | Version |
|---|---|
| JDK | 17+ |
| Android Studio | Ladybug or newer (recommended) |
| Android Gradle Plugin | 8.6.1 (pinned in `gradle/libs.versions.toml`) |
| Gradle | 8.7 |
| Kotlin | 2.0.20 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |

## Building

### Option A — Android Studio (easiest)

1. `File → Open` and select the `VI-Player` folder.
2. Android Studio will generate the missing Gradle wrapper JAR and sync.
3. Press **Run**.

### Option B — command line

The Gradle wrapper **JAR is not included** (it is a binary file). Generate it
once with a locally installed Gradle, then use `gradlew` normally:

```bash
cd VI-Player
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤32) | Read the audio library |
| `POST_NOTIFICATIONS` (API 33+) | Playback notification |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playback alive in the background |
| `WAKE_LOCK` | Continue playing with the screen off |

## Project structure

```
app/src/main/java/com/viplayer/
├── MainActivity.kt              Compose entry point
├── ViPlayerApp.kt               Application class
├── data/
│   ├── Models.kt                Song, Album, Artist, Playlist, enums
│   ├── MusicRepository.kt       MediaStore queries + grouping
│   ├── PlaylistStore.kt         DataStore-backed playlist persistence
│   └── SettingsStore.kt         DataStore-backed preferences
├── playback/
│   ├── PlaybackService.kt       MediaSessionService hosting ExoPlayer
│   ├── PlayerConnection.kt      MediaController bridge + UI state
│   └── EqualizerController.kt   Audio effect bound to the player session
└── ui/
    ├── ViApp.kt                 Navigation graph, bottom bar, mini player
    ├── ViViewModel.kt           Single source of truth
    ├── theme/Theme.kt           Colour schemes, accents, typography
    ├── components/              AlbumArt, SongRow, MiniPlayer, action sheet
    └── screens/                 Songs, Albums, Artists, Playlists,
                                 NowPlaying, Equalizer, Settings, Search
```

## Design notes

- **State flows one way.** `ViViewModel` owns the library and preferences;
  `PlayerConnection` owns playback. Composables only read state and dispatch
  commands.
- **Playback lives in a service.** The UI talks to it through a MediaController,
  never to `ExoPlayer` directly.
- **MediaStore column reads are defensive.** `artist_id` and `album_id` are not
  exposed by every OEM implementation, so look-ups go through helpers that fall
  back to a stable hash of the artist/album name.
- **The equalizer degrades quietly.** Effect support varies by device, so every
  platform call is guarded and the screen shows an explanation when the effect
  is unavailable.

## Known limitations

- **Artwork** is resolved through the legacy
  `content://media/external/audio/albumart/<id>` endpoint. It works on most
  devices but is not officially supported on Android 10+; a future revision
  should fall back to `ContentResolver.loadThumbnail` on the song URI.
- **Playlists store song IDs only.** If a track is removed from the device it
  simply drops out of the playlist rather than showing as missing.
- **No gapless or crossfade** yet.
- **Not yet compiled.** See below.

## Verification status

⚠️ This project has **not been compiled or run**. The machine it was written on
has no JDK, no Android SDK and no Gradle installed, so no build was possible.

What *has* been checked:
- Every Kotlin file passes a bracket/structure balance check.
- The Gradle version matrix (AGP 8.6.1 / Kotlin 2.0.20 / Compose BOM 2024.09.03 /
  Media3 1.4.1 / Gradle 8.7) is a known-compatible set.

What has *not* been checked:
- Actual compilation, resource linking, or runtime behaviour.

Expect to iterate on the first build. If you install JDK 17 and the Android SDK,
the errors can be worked through directly.
