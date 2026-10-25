# MusicPlayer

A native Android music player built with **Kotlin**, **Jetpack Compose**, and **AndroidX Media3**. The app scans the device's local music library, plays tracks through a Media3 playback service, and separates UI, business rules, and platform-specific media operations using Clean Architecture principles.

## Features

### Local music library

- Reads locally indexed music from Android's `MediaStore`.
- Displays track titles and available album artwork.
- Searches by track title and artist name.
- Sorts tracks alphabetically (A–Z) or by date added.
- Saves the selected sort option across app launches.
- Refreshes the library manually and observes MediaStore changes.
- Reconciles duplicate-like MediaStore records using file/path and metadata identity, and prefers a readable record when matching entries are found.
- Checks whether a media URI can be opened before adding the track to the list, helping avoid stale MediaStore entries that point to missing files.

### Playback

- Uses **Media3 ExoPlayer** for local audio playback.
- Runs playback in a `MediaSessionService`, allowing playback to continue outside the activity while Android permits it.
- Provides a full-screen **Now Playing** screen with album artwork, track metadata, a seek slider, and playback controls.
- Supports play/pause, next, previous, seeking, and repeat modes: Off, Repeat All, and Repeat One.
- Automatically advances through the playback queue when a track finishes, according to the selected repeat mode.
- Handles playback through a `MediaController` connected to the playback service.

### Persistent mini-player

- Saves the ID of the last played track between app launches.
- Shows the `NowPlayingBottomBar` for the current track or the last known track when playback is not active, provided that track is still present in the accessible music library.
- Hides the mini-player when there is no valid current or previously played track.
- Lets the user reopen the full player or resume playback from the mini-player.

> The saved track is a last-played selection; it does not, by itself, guarantee that playback automatically resumes after every process or device restart.

### Track management

- **Share:** shares one track through Android's system share chooser.
- **Rename:** renames the underlying audio file rather than only changing its displayed label, and preserves its file extension. Android's system approval flow is used when the OS requires additional write authorization.
- **Delete:** deletes a track using the platform's available storage API and requests system confirmation when required by Android's scoped-storage rules.
- **Multi-select:** long-press a track to enter selection mode, select all visible tracks, share selected tracks, or delete selected tracks.
- Provides track actions from the music list and the Now Playing screen.

### Interface

- Built with Jetpack Compose and Material 3.
- Uses a `LazyColumn` for the track list.
- Displays available album art with an app-icon fallback when artwork cannot be loaded.
- Follows the system light/dark theme; Android 12 and later can use dynamic system colors.

## Architecture

The app follows a feature-based Clean Architecture structure with MVVM for presentation state.

```text
Presentation (Jetpack Compose screens)
        |
        v
MusicViewModel (StateFlow, UI state, UI effects)
        |
        v
Domain use cases
        |
        v
Domain repository interfaces
        |
        v
Data implementations
        |
        +--> MediaStore and ContentResolver
        +--> Media3 MediaController / playback service
        +--> DataStore / SharedPreferences
```

### Layers

- **Core:** application and activity setup, plus platform-specific exceptions used for recoverable delete and rename requests.
- **Presentation:** Compose screens, navigation, theme, dialogs, UI models, one-way UI state/events, and `MusicViewModel`.
- **Domain:** track and playback models, repository contracts, track-list processing, and use cases. This layer contains the app's main playback, sorting, refresh, delete, rename, and queue operations.
- **Data:** MediaStore queries and file operations, Media3 playback implementation, repository implementations, and persisted preferences.
- **Dependency injection:** Hilt binds repository interfaces to their concrete implementations.
- **Playback service:** `PlaybackService` owns the ExoPlayer instance and exposes it through a Media3 `MediaSession`.

## Technology stack

| Component | Version / configuration |
|---|---|
| Language | Kotlin 2.1.21 |
| Android Gradle Plugin | 8.6.0 |
| Gradle wrapper | 8.7 |
| Minimum Android version | Android 9 (API 28) |
| Compile SDK | 35 |
| Target SDK | 34 |
| UI | Jetpack Compose, Material 3 |
| Compose BOM | 2025.06.01 |
| Navigation Compose | 2.8.9 |
| Media playback | AndroidX Media3 ExoPlayer, MediaSession, Media3 UI 1.5.0 |
| Dependency injection | Hilt 2.57.1 |
| Image loading | Coil Compose 2.6.0 |
| Preferences | Preferences DataStore 1.2.1; SharedPreferences for last-played track ID and rename-title fallbacks |
| Asynchronous work | Kotlin Coroutines and Flow |
| Unit testing | JUnit 4.13.2 |

## Project structure

The main source code is under `app/src/main/java/com/example/musicplayer/musicFeature/`.

```text
musicFeature/
├── core/
│   ├── MainActivity.kt
│   ├── MusicPlayerApplication.kt
│   └── platform/storage/
│       ├── RecoverableDeleteException.kt
│       └── RecoverableRenameException.kt
├── data/
│   ├── local/
│   │   └── MediaStoreAudioDataSource.kt
│   ├── playback/
│   │   └── Media3PlaybackRepository.kt
│   ├── preferences/
│   │   └── SortPreferencesDataSource.kt
│   └── repository/
│       ├── AudioRepositoryImpl.kt
│       └── SortPreferencesRepositoryImpl.kt
├── di/
│   └── AppModule.kt
├── domain/
│   ├── model/
│   │   ├── AudioTrack.kt
│   │   ├── PlaybackPosition.kt
│   │   ├── PlaybackState.kt
│   │   ├── RepeatMode.kt
│   │   └── SortType.kt
│   ├── repository/
│   │   ├── AudioRepository.kt
│   │   ├── PlaybackRepository.kt
│   │   └── SortPreferencesRepository.kt
│   ├── service/
│   │   └── TrackListProcessor.kt
│   └── usecase/
│       ├── ConnectPlaybackUseCase.kt
│       ├── DeleteAudioTracksUseCase.kt
│       ├── GetAudioTracksUseCase.kt
│       ├── NextTrackUseCase.kt
│       ├── ObserveLastPlayedTrackIdUseCase.kt
│       ├── ObservePlaybackPositionUseCase.kt
│       ├── ObservePlaybackStateUseCase.kt
│       ├── ObserveSortTypeUseCase.kt
│       ├── PlayTracksUseCase.kt
│       ├── PreviousTrackUseCase.kt
│       ├── PrunePlaybackQueueUseCase.kt
│       ├── RefreshAudioTracksUseCase.kt
│       ├── RenameAudioTrackUseCase.kt
│       ├── SeekToUseCase.kt
│       ├── SetRepeatModeUseCase.kt
│       ├── SetSortTypeUseCase.kt
│       ├── StopPlaybackUseCase.kt
│       └── TogglePlayPauseUseCase.kt
├── presentation/
│   ├── model/
│   │   ├── HomeUiState.kt
│   │   └── MusicEffect.kt
│   ├── navigation/
│   │   └── Navigation.kt
│   ├── screens/
│   │   ├── list/
│   │   │   ├── AudioListScreen.kt
│   │   │   ├── DeleteSongDialog.kt
│   │   │   ├── NowPlayingBottomBar.kt
│   │   │   ├── RenameSongDialog.kt
│   │   │   ├── SearchBar.kt
│   │   │   ├── SongAlbumArt.kt
│   │   │   └── SongItem.kt
│   │   └── player/
│   │       └── NowPlayingScreen.kt
│   ├── theme/
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── MusicPlayerApp.kt
│   ├── PermissionScreen.kt
│   └── viewmodel/
│       └── MusicViewModel.kt
└── service/
    └── PlaybackService.kt
```

Other important project files include the root `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`, and `app/src/main/AndroidManifest.xml`.

## Requirements

- Android Studio with support for the project's Android Gradle Plugin version.
- JDK 17 for the Gradle/Android build environment.
- Android SDK Platform 35 and the required SDK Build Tools.
- An Android device or emulator running **Android 9 (API 28) or later**.

## Build and run

### Clone the repository

```bash
git clone https://github.com/alimt-5/MusicPlayer.git
cd MusicPlayer
```

### Open in Android Studio

1. Open the `MusicPlayer` project root (the directory containing `settings.gradle.kts`).
2. Allow Gradle to sync and download the required dependencies.
3. Connect an Android device running API 28 or later, or start an emulator.
4. Select the `app` run configuration and press **Run**.

### Build from the command line

On Windows PowerShell or Command Prompt:

```powershell
.\gradlew.bat clean assembleDebug
```

On macOS or Linux:

```bash
./gradlew clean assembleDebug
```

The debug APK is normally generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run the available automated tests with:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Instrumented tests require a connected device or running emulator. The project currently contains basic test scaffolding; media permissions, rename/delete confirmation flows, and playback should also be validated on a real device.

## Permissions and storage behavior

The app requests access to local audio so it can build the music library.

- **Android 13 (API 33) and later:** `READ_MEDIA_AUDIO`; the app also requests `POST_NOTIFICATIONS` for playback notifications.
- **Android 9–12 (API 28–32):** `READ_EXTERNAL_STORAGE`. On Android 9, the app may also request `WRITE_EXTERNAL_STORAGE` for legacy file operations.
- **Foreground playback:** the manifest declares the foreground-service permissions required by a media playback service.

Android controls access to shared media. Depending on the Android version and file location, rename and delete can trigger a system confirmation dialog. A track may not appear if Android has not indexed it as music, the app lacks permission to read it, or its MediaStore URI cannot be opened.

## Troubleshooting

### The music list is empty

1. Open the app's system settings and confirm that Music/Audio permission is allowed.
2. Return to the app and tap **Refresh**.
3. Confirm that the files are accessible to Android and indexed by MediaStore. Audio files in locations excluded from indexing may not be listed.

### A track appears more than once or does not play

The data source checks whether each MediaStore URI can be opened and reconciles duplicate-like records using path and file metadata. If Android still returns stale library data, refresh the list after the file has been moved, renamed, or deleted. The app cannot play a file that the system no longer exposes through a readable URI.

## Repository
- Linkedin: [Alireza Motlagh](www.linkedin.com/in/alireza-motlagh-8b14a6436)
- GitHub: [alimt-5/MusicPlayer](https://github.com/alimt-5/MusicPlayer)
- Developer: [alimt-5](https://github.com/alimt-5)

