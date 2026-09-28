# LocalStream

LocalStream turns your Android phone into a local media server.

You can keep your movies, videos, music, and other media on your phone and stream them to another device using a browser or a media player such as VLC.

Everything runs over your local network. Your media does not need to be uploaded to a cloud service.

---

## What is LocalStream?

LocalStream lets you use your Android phone as a small personal media server.

Start the server on your phone, connect another device to the same network, and open the address shown in the app.

For example:

```text
http://[IP_ADDRESS]
```

Now you can browse and play your media using a standard web browser (e.g., Chrome, Firefox, Edge) or a compatible media player (e.g., VLC).

---

## Key Features

- **Media Indexing**: Scan and organize your media files (videos, audio, images) into a searchable library.
- **HTTP Streaming**: Stream media files directly to your browser or other devices on the same network.
- **Range Streaming**: Built-in support for HTTP Range requests for smooth playback without downloading the entire file.
- **Cross-Platform Compatibility**: Works with web browsers, VLC, and other media players that support HTTP streaming.
- **Simple UI**: Easy-to-use interface for starting/stopping the server and managing your media collection.

---

## How It Works

LocalStream runs as a background service on your Android device:

1. **Indexing**: The app scans your device's storage (with your permission) to discover media files.
2. **Server**: It starts a lightweight HTTP server on a specific port (default: 8080).
3. **Discovery**: The server advertises itself on the local network using mDNS/Bonjour, making it easy to find.
4. **Streaming**: Clients on your network can access the media through the provided URL. The server handles chunked streaming, allowing you to seek and play files without buffering the entire content.

---

## Repository Layout

This is a monorepo. The two apps share their data models through a
local path dependency so the wire contract is defined in one place.

```text
LocalStream/
├── apps/
│   ├── mobile/          # Flutter server app (HTTP server, library, clients)
│   └── tv/              # Flutter TV client (browse + play over HTTP)
├── packages/
│   ├── protocol/        # Shared models for the /api/v1 contract
│   │                    #   (ServerStatus, MediaItem, FolderEntry,
│   │                    #    ConnectedClient, LibraryView, ServerInfo)
│   └── media_core/      # Storage-agnostic media access interfaces
├── docs/                # API, architecture, streaming, development notes
├── server/web/          # Optional Node service for device feedback
└── tooling/             # Repository helper scripts
```

`packages/protocol` holds the serialized `/api/v1` shapes used by **both**
apps. The mobile app and the TV app each expose thin re-export shims under
`lib/models/`, so feature code keeps importing the local path while the
model itself exists exactly once. CI analyzes and tests every package as
well as every app, so a change to the shared contract cannot break one app
silently.

---

## Architecture

LocalStream uses a modern, modular architecture:

```text
┌─────────────────────────────────────────┐
│  LocalStream (Android App)            │
├─────────────────────────────────────────┤
│  - Flutter UI (Mobile & TV)             │
│  - Android Native Services              │
│    • HTTP Server (java.net.ServerSocket) │
│    • Storage Manager (SAF)              │
│    • Media Scanner                      │
│    • mDNS/NSD Discovery                 │
│    • Foreground Service                 │
│  - Platform Channels (Flutter <-> Kotlin) │
└─────────────────────────────────────────┘
      │                  │
      ▼                  ▼
   Media Files        HTTP/Network
(Internal Storage,     Stream
 USB, SD Card)          │
                         ▼
                  ┌──────────────┐
                  │              │
                  ▼              ▼
         ┌─────────────────────────┐
         │ Browser Client          │
         │ / watch/:id            │
         └─────────────────────────┘
                  │
                  ▼
         ┌─────────────────────────┐
         │ Media Players           │
         │ (VLC, Kodi, etc.)       │
         └─────────────────────────┘
```

---

## Getting Started

### Prerequisites

- Android device with **Android 13 (API 33)** or higher
- Internet browser on your computer or other device
- Access to the same Wi-Fi network

### Installation

1. **Install the app**: Download and install the LocalStream APK on your Android device.
2. **Grant permissions**: Open the app and grant storage permissions when prompted.
3. **Select media sources**: Tap **Select Folders** or **Add Media** to choose the folders containing your media files.
4. **Start the server**: Tap the **Start Server** button.

### Streaming

Once the server is running:

1. **Note the address**: Look for the URL displayed in the app, for example:
   ```text
   http://[IP_ADDRESS]
   ```
2. **Open in browser**: On your computer or another device, open a web browser and enter that address.
3. **Browse and play**: Navigate through your media files and click on any item to start streaming.

---

## Development

```text
# apps
cd apps/mobile && flutter pub get && flutter analyze && flutter test
cd apps/tv     && flutter pub get && flutter analyze && flutter test

# shared packages
cd packages/protocol   && flutter pub get && flutter analyze && flutter test
cd packages/media_core && flutter pub get && flutter analyze && flutter test

# release APK
flutter build apk --release
```

CI (`.github/workflows/ci.yml`) runs `analyze` and `test` for both apps
and for every package under `packages/` — the package list is discovered
automatically, so adding a package needs no workflow edit. Pushing a
`v*` tag builds both APKs and publishes them to the GitHub Release; the
in-app update checker compares that tag against the installed version.

Native Android unit tests live in `apps/mobile/android/app/src/test` and
run with:

```text
cd apps/mobile/android && ./gradlew test
```

Further notes are in [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md),
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and
[`docs/API.md`](docs/API.md).

---

## Advanced Features

### Android TV Client

LocalStream includes a dedicated Android TV client for a 10-foot UI experience.

- Install the separate `apps/tv` APK on your TV or Fire TV device.
- Use your remote's D-pad for navigation.
- Enjoy a clutter-free interface optimized for larger screens.

### File Discovery

LocalStream automatically discovers and indexes media files from:

- Internal storage
- USB OTG drives
- SD cards

Simply select the desired folders, and the app will monitor them for media files.

Storage is reached exclusively through the Storage Access Framework, so
media is never copied out of its original location unless you explicitly
import an item.

### Network Configuration

- The server automatically selects an available port (default: 8080).
- You can change the port in the app settings if needed.
- The server uses mDNS (Bonjour) to advertise itself on the network, making it discoverable by other devices.