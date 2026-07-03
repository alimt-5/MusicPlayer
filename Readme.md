# 🎵 MusicPlayer

A modern Android music player built with Jetpack Compose and Media3 ExoPlayer, following Clean Architecture principles.

## 📱 Features

- **Local Music Playback** – Browse and play audio files stored on your device
- **Smart Search** – Search tracks by title or artist name
- **Sort Options** – Organize your library alphabetically or by date added
- **Custom Scroll Bar** – Navigate through long playlists with a draggable scroll indicator
- **Now Playing Screen** – Full-screen player with seek slider, playback controls, and album art display
- **Repeat Modes** – Toggle between no repeat, repeat one, or repeat all
- **Auto-Play Next** – Automatically plays the next track when the current one ends
- **Persistent Playback** – Music continues playing in the background with a fully customizable notification
- **Dark & Light Themes** – Dynamic theming with Material 3 support (Android 12+ dynamic color)
- **Album Art Display** – Shows embedded album artwork in lists and player screen
- **Smooth Scrolling** – Optimized lazy column with image caching for a lag-free experience

## 🏗️ Architecture

The project follows **Clean Architecture** with three layers:

- **Data Layer** – `AudioRepositoryImpl`, MediaStore queries, and ExoPlayer setup
- **Domain Layer** – `AudioTrack` entities, `GetAudioTracksUseCase`, and repository interfaces
- **Presentation Layer** – Jetpack Compose UI screens (`AudioListScreen`, `NowPlayingScreen`), `AudioViewModel` with StateFlow, and custom UI components

## 🛠️ Tech Stack

- **UI Toolkit**: Jetpack Compose (Material 3)
- **Media Player**: Media3 ExoPlayer with MediaSession
- **Image Loading**: Coil with disk/memory caching
- **State Management**: Kotlin Flows (StateFlow, SharedFlow)
- **Architecture**: Clean Architecture + MVVM
- **Navigation**: Jetpack Navigation Compose
- **Background Playback**: MediaSessionService with foreground notification
- **Image Caching**: Coil with custom ImageLoader
- **Min SDK**: Android 8 (API 26)

## 📁 Project Structure

```
app/src/main/java/com/example/musicplayer/
├── core/                          # MainActivity & app setup
├── data/                          # Data layer
│   ├── AudioRepositoryImpl.kt     # MediaStore implementation
│   └── PlaybackService.kt         # Background playback service
├── domain/                        # Domain layer
│   ├── AudioTrack.kt              # Audio entity
│   ├── AudioRepository.kt         # Repository interface
│   └── GetAudioTracksUseCase.kt   # Use case
├── presentation/                  # UI layer
│   ├── AudioListScreen/           # List screen components
│   │   ├── AudioListScreen.kt
│   │   ├── SearchBar.kt
│   │   ├── SongsList.kt
│   │   ├── ScrollBar.kt
│   │   └── NowPlayingBottomBar.kt
│   ├── viewModel/
│   │   └── AudioViewModel.kt      # ViewModel with state management
│   ├── NowPlayingScreen.kt        # Full-screen player
│   ├── HomeUiState.kt             # UI state definitions
│   └── Navigation.kt              # Navigation routes
└── ui/theme/                      # Theming
    ├── Theme.kt
    ├── Color.kt
    └── Type.kt
```

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK with minimum API 26

### Clone and Build

```bash
git clone https://github.com/alimt-5/MusicPlayer.git
cd MusicPlayer
```

Open the project in Android Studio and sync Gradle.

### Run the App

1. Connect an Android device or start an emulator (API 26+)
2. Select the `app` module and click **Run** ▶️

### Required Permissions

The app requests the following permissions at runtime:

- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` – To access local audio files
- `POST_NOTIFICATIONS` (Android 13+) – For playback notifications


### Image Loading

Coil is configured with memory and disk caching. The `ImageLoader` is provided via `CompositionLocalProvider` in `MainActivity` for optimal performance.


## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/alimt-5/MusicPlayer/issues).

## 📬 Contact

**Developer**: [alimt-5](https://github.com/alimt-5)

---

*Made with ❤️ using Jetpack Compose*