# LocalStream System Architecture

LocalStream is a production-grade Android application that turns an Android mobile device into a local LAN media server. It enables any device on the same Wi-Fi network or mobile hotspot (desktop browsers, mobile browsers, Smart TVs, and external players like VLC) to browse, stream, and seek media hosted on the phone without internet access or third-party cloud infrastructure.

This document serves as the **Phase 1 Architecture Audit** and authoritative architectural blueprint defined in [`modular.md`](../modular.md).

---

## 1. High-Level System Topology

```text
                           ┌────────────────────────┐
                           │   Flutter Mobile UI    │
                           │     (apps/mobile)      │
                           └───────────┬────────────┘
                                       │ MethodChannel / EventChannel
                                       ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                   Native Android / Kotlin Server Subsystem              │
 │                   (apps/mobile/android/.../localstream_mobile)         │
 │                                                                        │
 │  ┌──────────────────────────────────────────────────────────────────┐  │
 │  │              LocalStreamService (Android Foreground Service)     │  │
 │  │   - START_STICKY, WakeLock, Persistent Notification, Sticky      │  │
 │  │   - Lifecycle decoupled from Flutter UI Activity                 │  │
 │  └───────┬──────────────────────────┬────────────────────────┬──────┘  │
 │          │                          │                        │         │
 │          ▼                          ▼                        ▼         │
 │  ┌────────────────┐         ┌───────────────┐        ┌──────────────┐  │
 │  │HttpMediaServer │         │StorageManager │        │ Network &    │  │
 │  │  (Port 8080/0) │         │ - SAF Trees   │        │ Discovery    │  │
 │  │  - Dispatch IO │         │ - Content URIs│        │ - MdnsService│  │
 │  │  - Semaphore32 │         │ - MediaCache  │        │ - NetMonitor │  │
 │  └───────┬────────┘         └───────┬───────┘        └──────────────┘  │
 └──────────┼──────────────────────────┼──────────────────────────────────┘
            │                          │
            │ HTTP / Range / SSE       │ Content streams
            ▼                          ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                           Connected LAN Clients                        │
 │                                                                        │
 │  ┌───────────────────────┐  ┌───────────────────┐  ┌────────────────┐  │
 │  │   Web Browser Client  │  │ Flutter TV Client │  │ External App   │  │
 │  │   - Single-Page SPA   │  │    (apps/tv)      │  │ (VLC / MPV /   │  │
 │  │   - HTML5 Range Player│  │ - mDNS Discovery  │  │  Kodi / Exo)   │  │
 │  │   - Diagnostics HUD   │  │ - REST + Player   │  │ - Direct Range │  │
 │  └───────────────────────┘  └───────────────────┘  └────────────────┘  │
 └────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Monorepo Structure & Module Boundaries

The repository is structured as a multi-package workspace:

```text
local_stream/
├── apps/
│   ├── mobile/                    # Primary Flutter mobile application
│   │   ├── android/               # Android host project & Kotlin server implementation
│   │   ├── lib/                   # Flutter presentation, services, and state
│   │   └── test/                  # Unit and widget tests for mobile
│   └── tv/                        # Lightweight Flutter Android TV client
│       ├── lib/                   # TV-optimized D-Pad UI & media client
│       └── test/                  # TV unit tests
├── packages/
│   ├── media_core/                # Media repository & source abstraction contracts
│   └── protocol/                  # Shared data models (MediaItem, ServerInfo, enums)
├── server/
│   └── web/                       # Node.js device telemetry/feedback microservice
├── docs/                          # Architecture, API, Streaming, and Dev documentation
├── tooling/                       # Scripts and automation utilities
└── modular.md                     # Master refactoring roadmap and design principles
```

### Module Boundary Matrix

| Layer / Module | Allowed Outward Dependencies | Forbidden Inward Dependencies |
| :--- | :--- | :--- |
| **`packages/protocol`** | Pure Dart SDK | Flutter, Android SDK, HTTP clients, UI |
| **`packages/media_core`** | `protocol`, Pure Dart SDK | Flutter UI, Platform Channels, Android Context |
| **`apps/mobile/lib`** | `protocol`, `media_core`, Flutter SDK | Android Kotlin internals, Direct raw sockets |
| **`apps/mobile/android`**| Android Framework, Kotlin Stdlib, Coroutines | Flutter UI widgets (communicates only via Channel) |
| **`apps/tv/lib`** | `protocol`, Flutter SDK, HTTP client | Native server internals (pure HTTP consumer) |
| **`server/web`** | Node.js stdlib | LocalStream application code |

---

## 3. Native Server Lifecycle & State Machine

The native Android server is orchestrated by `LocalStreamService`, implemented as an Android Foreground Service to prevent OS memory reclamation during background media streaming.

### State Transition Diagram

```text
               ┌─────────────┐
               │   STOPPED   │
               └──────┬──────┘
                      │ ACTION_START / startServer(port)
                      ▼
               ┌─────────────┐
        ┌─────►│  STARTING   │◄──── (Retry / Port Fallback)
        │      └──────┬──────┘
        │             │ ServerSocket bound & Coroutine accept loop started
        │             ▼
        │      ┌─────────────┐
        │      │   RUNNING   │
        │      └──────┬──────┘
        │             │ ACTION_STOP / stopServer()
        │             ▼
        │      ┌─────────────┐
        │      │  STOPPING   │
        │      └──────┬──────┘
        │             │ ServerSocket closed & Coroutine workers cancelled
        │             ▼
        │      ┌─────────────┐
        │      │   STOPPED   │
        │      └─────────────┘
        │
        └── SocketException / Bind Error
               ┌─────────────┐
               │    ERROR    │
               └─────────────┘
```

### Lifecycle Key Guarantees

1. **Independent Lifecycle**: The server lifecycle is completely detached from Flutter Activity lifecycles. When `MainActivity` is destroyed or backgrounded, `LocalStreamService` continues operating in `START_STICKY` mode.
2. **Foreground Notification Requirement**: Upon receiving `ACTION_START`, `startForeground()` is invoked immediately with a persistent notification (Notification ID `1001`, channel `localstream_server_channel`, `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on Android 14+), satisfying the 5-second Android ANR deadline.
3. **Port Binding Fallback**:
   - The server first attempts to bind to the requested port (default `8080`) on all interfaces (`0.0.0.0`).
   - If the preferred port is occupied, it automatically falls back to an ephemeral system-allocated port (`new ServerSocket(0)`).
   - The actual bound port is communicated to `ServerStateHolder`, updating the notification and Flutter UI.
4. **Concurrency & Worker Bounds**:
   - Socket acceptance runs inside an unconfined supervisor coroutine scope on `Dispatchers.IO`.
   - Concurrent active connections are capped at `32` via `kotlinx.coroutines.sync.Semaphore` to protect phone CPU and battery.
   - Sockets enforce a `soTimeout = 30000ms` (30 seconds) to purge inactive or stalled TCP connections.

---

## 4. Flutter / Native Android Bridge

Communication between the Flutter UI layer and the native server is coordinated across dedicated platform channels:

```text
┌─────────────────────────────────┐                 ┌────────────────────────────────┐
│        Flutter Services         │                 │      Android Native Host       │
│  (PlatformBridge/NativeBridge)  │                 │   (LocalStreamMethodChannel)   │
└────────────────┬────────────────┘                 └───────────────┬────────────────┘
                 │                                                  │
                 │ MethodChannel('localstream/mobile')              │
                 │ ────────────────────────────────────────────────►│ (startServer, stopServer,
                 │ ◄────────────────────────────────────────────────│  getStatus, listFiles, etc.)
                 │                                                  │
                 │ EventChannel('localstream/mobile/events')        │
                 │ ◄════════════════════════════════════════════════│ (status_changed,
                 │                                                  │  library_changed,
                 │                                                  │  import, network_changed)
```

### Method Channel API Surface (`localstream/mobile`)

| Method Name | Arguments | Return Type | Description |
| :--- | :--- | :--- | :--- |
| `startServer` | `{port: int}` | `bool` | Starts the foreground service and HTTP server |
| `stopServer` | _None_ | `bool` | Stops HTTP server and shuts down service |
| `getStatus` | _None_ | `Map<String, dynamic>` | Returns snapshot of current state, port, clients |
| `getAppVersion` | _None_ | `Map<String, dynamic>` | Returns `versionName` and `versionCode` |
| `getNetworkAddresses`| _None_ | `List<String>` | Returns active IPv4 addresses of the device |
| `getClients` | _None_ | `List<Map>` | Returns all tracked client device records |
| `removeClient` | `{ip: String}` | `bool` | Clears a client device record from store |
| `listFiles` | _None_ | `List<Map>` | Lists all currently imported/indexed media files |
| `addSafFolder` | `{uri: String}` | `int` | Indexes files from a selected SAF directory URI |
| `pickMediaFiles` | _None_ | `int` | Opens Android document picker for file selection |
| `removeMediaItem` | `{id: String}` | `bool` | Deletes imported media or unindexes SAF entry |
| `importMediaItem` | `{uri: String, ...}` | `Map` | Copies shared media item to app private cache |
| `getPendingShares` | _None_ | `List<String>` | Polls pending `content://` URIs from share intent|
| `getPinState` | _None_ | `Map` | Checks whether access PIN protection is enabled |
| `setAccessPin` | `{pin: String}` | `bool` | Updates server access PIN |
| `clearAccessPin` | _None_ | `bool` | Disables access PIN requirement |

### Event Stream (`localstream/mobile/events`)

The native layer pushes real-time events over a single broadcast event channel:
- `status_changed`: Fired on server state transition (port, client count, active streams, transferred bytes).
- `library_changed`: Fired when folders are added, files are removed, or new media is indexed.
- `import`: Progress events for ongoing file imports (`{uri, readBytes, totalBytes, done}`).
- `network_changed`: Fired when device network interfaces change (Wi-Fi connected/disconnected).

---

## 5. Storage & Media Abstraction

LocalStream abstracts disparate storage backing mechanisms behind a unified interface:

```text
                              ┌───────────────────┐
                              │    MediaSource    │
                              │    (Interface)    │
                              └─────────┬─────────┘
                                        │
             ┌──────────────────────────┼──────────────────────────┐
             ▼                          ▼                          ▼
  ┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
  │    SafMediaSource    │   │ContentUriMediaSource │   │ ImportedMediaSource  │
  │ - DocumentFile Tree  │   │ - Generic Share URI  │   │ - Internal App Cache │
  │ - Read-only Range    │   │ - Temporary access   │   │ - Copy of external   │
  │ - Persistent Grant   │   │ - Transient streams  │   │ - Full read-write    │
  └──────────────────────┘   └──────────────────────┘   └──────────────────────┘
```

### Storage Mechanisms

1. **SAF Tree Persistence (`SafMediaSource`)**:
   - The user selects a directory via the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`).
   - Persistable URI permission flags (`FLAG_GRANT_READ_URI_PERMISSION`) are taken to survive device reboots.
   - Files are indexed with folder tree hierarchy preserved, generating virtual folder IDs.
2. **Android Share Intent Handler (`ContentUriMediaSource` & `ShareReceiverActivity`)**:
   - Accepts incoming media from ANY external Android application (File Managers, Chat apps, Downloads) via `ACTION_SEND` and `ACTION_SEND_MULTIPLE`.
   - Never branches on sender application name.
   - Media is made streamable immediately via `content://` descriptor range reads or imported into app storage.
3. **Internal App Cache (`ImportedMediaSource`)**:
   - Stored in `context.filesDir/media_imports`.
   - Allows permanent availability even if the original external source is unmounted or removed.
4. **Metadata & Thumbnail Cache (`MediaMetadataCache`)**:
   - Asynchronously extracts durations, video dimensions, and JPEG thumbnails via `MediaMetadataRetriever`.
   - Cached to disk in app private cache directory to ensure fast library loading.

---

## 6. Web Client Architecture

The web client is an embedded Single Page Application (SPA) delivered directly by `WebClientHtml.kt` at `GET /` and `GET /watch/:id`.

### Design & Architectural Principles

- **Zero-Dependency Vanilla Implementation**: Built without external heavy frontend frameworks or CDN requirements; functions entirely offline on air-gapped local Wi-Fi.
- **REST + SSE Communication**:
  - `GET /api/v1/files`: Fetches media library items and folder structure.
  - `GET /api/v1/status`: Polling heartbeat for connection monitoring (2.5s interval).
  - `GET /api/v1/events`: Server-Sent Events for instant UI updates when media is added.
- **Integrated Video Player**:
  - Native `<video>` element with custom liquid glass aesthetic controls.
  - Range streaming consumer: never buffers entire files into memory; relies on standard HTTP Range requests.
  - Video diagnostic HUD: Real-time calculation of bandwidth throughput, movie bitrate, buffer health ahead of playhead, and presentation frame rate (using `requestVideoFrameCallback`).
  - Audio codec compatibility detection: Audio track analysis to warn user if container uses unsupported browser audio codecs (e.g. AC-3/E-AC-3, DTS) with immediate one-click VLC stream launch fallback.

---

## 7. Identified Technical Debt & Modular Refactoring Targets

In accordance with [`modular.md`](../modular.md), the following architectural refactoring targets are identified for subsequent phases:

| Area | Current State | Target Architecture (modular.md) |
| :--- | :--- | :--- |
| **Flutter Bridge** | Fragmented across `PlatformBridge` and `NativeBridge` with duplicate code in `settings_screen.dart` | Single unified `ServerController` and `MediaRepository` implementation hiding native channels |
| **HTTP Request Handler** | `HttpRequestHandler.kt` is a 536-line god class handling parsing, auth, routing, and media dispatch | Route handlers isolated into dedicated controller classes (`AuthController`, `MediaStreamController`, `LibraryController`) |
| **Web Client Delivery** | 1,551 lines of raw HTML/CSS/JS embedded as a Kotlin string template inside `WebClientHtml.kt` | Modular source assets in `server/web/src/` with an automated bundling or resource embedding pipeline |
| **Domain Layer** | Data models (`MediaItem`, `ServerStatus`) mixed between Flutter models and raw map transformations | Pure domain models in `packages/protocol` and `packages/media_core` without UI or channel coupling |
| **Access Control** | PIN check mixed directly inside `HttpRequestHandler` request dispatcher | Interceptor/middleware pattern cleanly separating authentication from endpoint logic |
