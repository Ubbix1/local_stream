You are building LocalStream, a production-quality Android local media server.

IMPORTANT:
Do not build the TV client yet.
Do not build the browser frontend yet.
This task is ONLY the Android PHONE SERVER application.

The server app must be built with:

- Flutter + Dart for the application UI/business layer
- Kotlin for Android-native functionality
- Android Foreground Service for the persistent server
- Android Storage Access Framework for storage access
- Native Android HTTP server implementation
- mDNS/NSD discovery
- HTTP Range-based media streaming
- Clean architecture with strict failure isolation

==================================================
1. PRODUCT PURPOSE
==================================================

LocalStream turns an Android phone into a local media server.

A user can:

1. Select media/storage locations.
2. Start a local HTTP media server.
3. Share a media file from ANY Android application using Android Share.
4. Add/import or temporarily expose that media to LocalStream.
5. Allow devices on the same LAN/hotspot to access the server.
6. Stream movies directly through HTTP.
7. Allow browser clients, TV clients, and future applications to consume the API.
8. Discover the server through mDNS/NSD.
9. Keep the server running when the Flutter UI is closed/backgrounded.

Telegram is ONLY an example source.

DO NOT implement Telegram-specific logic.

The share system must work generically with Android ACTION_SEND and ACTION_SEND_MULTIPLE intents and content:// URIs.

==================================================
2. CORE ARCHITECTURE
==================================================

Use this architecture:

Flutter/Dart
    |
    | MethodChannel / Platform Interface
    v
Kotlin Android Layer
    |
    +-- Foreground Service
    +-- HTTP Server
    +-- Storage Access Framework
    +-- Share Intent Handler
    +-- NSD/mDNS
    +-- Network information
    |
    v
MediaRepository
    |
    +-- SAF source
    +-- Imported/app-managed source
    +-- Shared content URI source
    |
    v
HTTP Media Server

Flutter is responsible for:

- UI
- navigation
- state management
- application models
- settings
- server status
- media library UI
- client status UI
- logs/status presentation
- API/protocol models

Kotlin is responsible for:

- Android lifecycle
- foreground service
- HTTP server
- Storage Access Framework
- content URI access
- Android share intents
- mDNS/NSD
- network interfaces/IP discovery
- low-level file/stream access
- long-running server process

Do not move heavy media streaming through Flutter/Dart unnecessarily.

==================================================
3. PROJECT STRUCTURE
==================================================

Create a clean structure similar to:

apps/mobile/

lib/
    core/
        errors/
        result/
        logging/
        constants/
        utilities/

    models/
        media_item.dart
        media_source.dart
        server_info.dart
        server_status.dart
        connected_client.dart

    repositories/
        media_repository.dart
        server_repository.dart

    services/
        server_service.dart
        media_service.dart
        discovery_service.dart
        share_service.dart

    features/
        home/
        library/
        server/
        shared_media/
        clients/
        settings/

    native/
        platform_bridge.dart

android/app/src/main/kotlin/.../

    server/
        LocalStreamService.kt
        HttpMediaServer.kt
        HttpRequestHandler.kt
        RangeRequestParser.kt

    storage/
        StorageManager.kt
        SafMediaSource.kt
        ContentUriMediaSource.kt
        ImportedMediaSource.kt

    sharing/
        ShareReceiverActivity.kt
        ShareIntentParser.kt

    discovery/
        MdnsService.kt

    network/
        NetworkInfoProvider.kt

    bridge/
        LocalStreamMethodChannel.kt

Keep responsibilities separated.

Do not create one giant Kotlin class.

==================================================
4. SERVER LIFECYCLE
==================================================

The server must run independently from the Flutter UI.

Use:

Android Foreground Service
        |
        +-- HTTP server
        +-- mDNS service
        +-- server state
        +-- active connections

Flutter UI connects to the service through a platform bridge.

Required states:

STOPPED
STARTING
RUNNING
STOPPING
ERROR

The Flutter application closing must NOT automatically terminate the server.

Stopping the server must explicitly stop the foreground service.

When the server is running, settings that would invalidate the running server should be protected/locked.

==================================================
5. HTTP SERVER
==================================================

Implement a lightweight embedded HTTP server.

Do NOT use Flutter's HTTP client as the actual media server.

The server must support:

GET / 
GET /api/v1/info
GET /api/v1/status
GET /api/v1/files
GET /api/v1/files/:id
GET /api/v1/stream/:id

Optional future endpoint:

POST /api/v1/play/:id

The API must be client-agnostic.

It must NOT assume the client is the LocalStream TV app.

Future clients may include:

- browser
- Android TV
- desktop
- another mobile application
- third-party API client

==================================================
6. HTTP RANGE STREAMING
==================================================

This is a CRITICAL feature.

Implement proper HTTP byte-range support.

Example:

GET /api/v1/stream/f_123

Range: bytes=1000000-

Response:

206 Partial Content

Headers should include appropriate:

Content-Type
Content-Length
Content-Range
Accept-Ranges: bytes

Support:

- full file requests
- partial requests
- seeking
- large files
- start/end ranges
- suffix ranges where appropriate
- invalid range handling

Invalid ranges must return a controlled HTTP response.

They must NEVER crash the server.

Use streaming I/O.

DO NOT load an entire movie into RAM.

A 20GB movie must be streamed from storage in chunks.

==================================================
7. MEDIA SOURCE ABSTRACTION
==================================================

Do not assume every media file is a normal filesystem path.

Create an abstraction:

MediaSource

It should conceptually support:

- id
- displayName
- mimeType
- size
- open()
- readRange()
- exists()
- metadata()

Implement sources for:

1. Storage Access Framework
2. Imported/app-managed files
3. Shared content:// URIs

The HTTP server should not care where the file came from.

It should only interact with MediaSource.

==================================================
8. ANDROID SHARE SUPPORT
==================================================

Register the app as a generic Android share target.

Support:

ACTION_SEND
ACTION_SEND_MULTIPLE

Accept generic media content.

Do NOT check:

"Is this Telegram?"

Instead inspect:

- URI
- MIME type
- metadata
- accessibility
- readable stream
- file size

Potential sources:

Telegram
WhatsApp
Chrome
Files
Google Drive
Gallery
File managers
Other applications

All must follow the same pipeline.

Share:

Android application
    |
    v
content:// URI
    |
    v
ShareIntentParser
    |
    v
MediaSource
    |
    v
MediaRepository

If the source provides an inaccessible or invalid URI, gracefully reject it.

Never crash.

==================================================
9. IMPORT VS TEMPORARY SOURCE
==================================================

Design the system so that it can support two modes:

STREAM_REFERENCE
IMPORT_TO_LIBRARY

For STREAM_REFERENCE:

Use the provided content URI while it remains accessible.

For IMPORT_TO_LIBRARY:

Copy the source into LocalStream-managed storage.

Do not assume either mode is always possible.

The architecture must allow future UI selection between them.

For MVP, implement the safer path first if necessary, but keep the abstraction extensible.

==================================================
10. STORAGE ACCESS FRAMEWORK
==================================================

Support:

- internal storage
- user-selected folders
- SD cards where available
- USB/OTG where Android exposes them through SAF

Persist SAF permissions using Android's persisted URI permissions.

Do not assume raw filesystem paths.

Handle:

- permission revoked
- storage removed
- USB disconnected
- SD card removed
- file deleted
- file renamed
- inaccessible URI

All failures must be recoverable.

==================================================
11. mDNS / NSD
==================================================

Advertise the LocalStream HTTP server through Android NSD/mDNS.

Service concept:

LocalStream
_http._tcp

Advertise:

- service name
- port

Do not make discovery a dependency for HTTP operation.

If mDNS fails:

HTTP server MUST continue running.

The user should still be able to connect using:

http://PHONE_IP:PORT

If possible, expose the server IP addresses through the app.

==================================================
12. NETWORKING
==================================================

The server should bind appropriately for LAN access.

Do not bind only to:

127.0.0.1

It must be reachable by other devices on the local network/hotspot.

Example:

http://192.168.1.25:8080

The app should detect useful local IPv4 addresses.

Do not assume the phone's IP is always the same.

Support Wi-Fi and phone hotspot scenarios where Android permits it.

==================================================
13. WEB CLIENT COMPATIBILITY
==================================================

The server is intended to eventually support:

http://PHONE_IP:PORT

from a normal browser.

Therefore:

- use standard HTTP
- use standard MIME types
- support Range requests
- support HEAD where useful
- provide clean API responses
- do not require a custom LocalStream client
- do not require WebSocket for basic movie playback

The future web UI will consume this server.

Do NOT build the web UI in this task.

==================================================
14. MEDIA TYPES
==================================================

Create MIME detection and media validation.

Initial target:

video/mp4
video/x-matroska
video/webm
video/quicktime
audio/*
image/*

Do not hardcode only MP4.

MKV should be treated as a valid media container.

Do not promise that every codec inside every container is browser-compatible.

The server's responsibility is to provide the file.

Transcoding is a future feature.

==================================================
15. FAILURE ISOLATION
==================================================

THIS IS ONE OF THE MOST IMPORTANT REQUIREMENTS.

No individual failure may terminate:

- Flutter UI
- HTTP server
- foreground service
- unrelated streams
- media library
- mDNS
- other clients

Every externally controlled operation must have a failure boundary.

Examples:

Bad shared URI
    -> fail only that share operation

Broken file
    -> fail only that file

USB removed
    -> terminate affected stream cleanly

Client disconnect
    -> clean up that client only

Invalid HTTP request
    -> return HTTP error only

Invalid Range
    -> return 416 or appropriate response

mDNS failure
    -> disable discovery but keep server alive

Storage permission failure
    -> report unavailable source

Out-of-memory risk
    -> never buffer entire files

Unexpected exception
    -> catch at component boundary
    -> log internally
    -> recover where possible

Do NOT allow exceptions to cross uncontrolled boundaries.

==================================================
16. UI ERROR POLICY
==================================================

Do NOT expose:

- stack traces
- Java exceptions
- Kotlin exceptions
- Dart exceptions
- HTTP internals
- implementation details
- debugging information

to normal users.

Instead use human-readable states.

Examples:

"Couldn't add this file."

"This file is no longer available."

"Unable to start the server."

"Storage permission is no longer available."

"Couldn't play this media."

Technical details belong in internal logs.

Do not create a giant error console in the main UI.

==================================================
17. INTERNAL LOGGING
==================================================

Create structured internal logging.

Categories:

SERVER
HTTP
STORAGE
SHARE
STREAM
DISCOVERY
NETWORK
LIFECYCLE

Every log should contain useful context where appropriate:

timestamp
component
operation
media ID
request ID
error category

Do not log sensitive file contents.

Do not log authentication secrets.

Keep logs bounded/rotated.

Logging must never itself crash the application.

==================================================
18. RESOURCE MANAGEMENT
==================================================

Every stream must properly close:

- InputStream
- OutputStream
- file descriptors
- HTTP connections
- coroutine/job resources
- temporary files

Use try/finally or equivalent structured resource handling.

A disconnected TV/browser must not leave an open file forever.

Implement connection cleanup.

Do not leak resources when exceptions occur.

==================================================
19. CONCURRENCY
==================================================

The server must support multiple clients.

Example:

Phone
 ├── TV streaming Movie A
 ├── Laptop browsing files
 └── Browser streaming Movie B

One client must not block unrelated clients.

Do not use a single global lock around all streaming.

Use bounded concurrency where appropriate.

Do not create unbounded threads/coroutines per connection.

==================================================
20. SECURITY BASELINE
==================================================

MVP is LAN/local-network focused.

Do not expose the server to the public internet.

Add basic safeguards:

- path traversal prevention
- no arbitrary filesystem path access
- only registered MediaSource IDs can be streamed
- validate requested IDs
- validate Range headers
- limit malformed requests
- don't expose internal filesystem paths
- don't trust client-provided filenames
- sanitize displayed names

Authentication can be added later.

HTTPS can be added later.

==================================================
21. SERVER API RESPONSE FORMAT
==================================================

Use consistent JSON.

Example:

GET /api/v1/info

{
  "name": "LocalStream",
  "version": "0.1.0",
  "deviceName": "...",
  "serverVersion": "...",
  "port": 8080,
  "protocolVersion": "1"
}

GET /api/v1/status

{
  "state": "running",
  "port": 8080,
  "clients": 2,
  "uptime": 12345
}

GET /api/v1/files

{
  "items": [
    {
      "id": "f_123",
      "name": "Movie.mkv",
      "type": "video",
      "mimeType": "video/x-matroska",
      "size": 1234567890
    }
  ]
}

Keep the protocol versioned:

/api/v1/...

==================================================
22. SERVER STATUS
==================================================

Expose enough information for Flutter:

- stopped
- starting
- running
- stopping
- error
- IP addresses
- port
- uptime
- active clients
- active streams
- bytes transferred
- server version

Do not expose internal exception text to normal UI.

==================================================
23. FLUTTER UI
==================================================

Build a clean functional MVP UI.

Screens:

1. Home
2. Library
3. Shared Media
4. Server
5. Connected Clients
6. Settings

Home should show:

Server status
Start/Stop
LAN address
Port
Number of media items
Connected clients

Example:

LocalStream

● Server Running

http://192.168.1.25:8080

[ Stop Server ]

Library
127 media items

Clients
2 connected

Do not over-design the UI yet.

Architecture and reliability are more important than visual polish.

==================================================
24. STATE MANAGEMENT
==================================================

Use a predictable state-management architecture.

Do not put business logic directly inside widgets.

Separate:

UI
state
repository
service
platform layer

All native communication must go through a small well-defined bridge.

==================================================
25. TESTING
==================================================

Create tests for:

Dart:

- API models
- media model parsing
- state transitions
- URI handling
- error mapping

Kotlin:

- Range parsing
- MIME detection
- media ID validation
- path traversal protection
- share intent parsing
- server lifecycle
- storage failures

Integration tests:

1. Start server
2. Browse files
3. Stream MP4
4. Stream MKV
5. Range request
6. Seek
7. Multiple clients
8. Client disconnect
9. Invalid Range
10. Missing file
11. Permission revoked
12. USB removed
13. mDNS unavailable
14. Network changes
15. Share valid media
16. Share invalid content
17. ACTION_SEND_MULTIPLE
18. Server survives Flutter UI closure

==================================================
26. STRESS TESTING
==================================================

The final implementation must be tested against:

- 1GB file
- 5GB file
- 10GB+ file
- multiple simultaneous streams
- repeated seeking
- rapid connect/disconnect
- network interruption
- Wi-Fi reconnect
- storage removal
- malformed requests

Do not load complete media files into memory.

==================================================
27. DO NOT IMPLEMENT YET
==================================================

Do NOT implement:

- FFmpeg transcoding
- DLNA/UPnP
- HTTPS
- authentication
- cloud storage
- internet streaming
- Chromecast
- screen mirroring
- TV application
- browser frontend

However, keep the architecture extensible for these features later.

==================================================
28. DEVELOPMENT PHASES
==================================================

Implement in this order:

PHASE 1
Project skeleton
Flutter ↔ Kotlin bridge
Server lifecycle

PHASE 2
HTTP server
/api/v1/info
/api/v1/status

PHASE 3
Storage Access Framework
MediaRepository

PHASE 4
/api/v1/files
/api/v1/files/:id

PHASE 5
/api/v1/stream/:id
HTTP Range streaming

PHASE 6
Foreground Service
Server survives UI lifecycle

PHASE 7
Generic Android Share Intent

PHASE 8
mDNS/NSD

PHASE 9
Failure isolation
logging
resource cleanup

PHASE 10
Stress/integration testing

After each phase:
- compile
- run tests
- fix errors
- do not leave broken placeholder code

==================================================
29. CODE QUALITY RULES
==================================================

Write production-quality code.

Avoid:

- giant classes
- global mutable state
- duplicated protocol definitions
- hardcoded IP addresses
- hardcoded storage paths
- Telegram-specific code
- swallowing exceptions without logging
- exposing exceptions to UI
- loading large files into RAM
- blocking the main Flutter isolate
- blocking Android main thread
- unsafe path construction

Prefer:

- interfaces
- dependency injection where useful
- immutable models
- typed results
- structured concurrency
- clear ownership
- testable components
- explicit lifecycle management

==================================================
30. DEFINITION OF DONE
==================================================

The server app is considered functional when:

[ ] App starts
[ ] Server starts
[ ] Foreground service works
[ ] Server survives leaving Flutter UI
[ ] LAN IP is displayed
[ ] Browser can reach the server
[ ] /api/v1/info works
[ ] /api/v1/status works
[ ] Files can be listed
[ ] Media IDs are safe
[ ] MP4 streams
[ ] MKV streams
[ ] HTTP Range works
[ ] Seeking works
[ ] Multiple clients work
[ ] Generic Android Share works
[ ] Shared content URI works
[ ] Invalid shared content doesn't crash
[ ] Missing file doesn't crash
[ ] USB/storage failure doesn't crash
[ ] Client disconnect doesn't crash
[ ] mDNS failure doesn't crash server
[ ] Flutter UI errors don't kill server
[ ] Native errors don't propagate into UI
[ ] No entire movie is loaded into RAM
[ ] Internal logging works
[ ] Tests pass
[ ] No known lifecycle/resource leaks

==================================================
FINAL INSTRUCTION
==================================================

Do not take shortcuts that compromise the architecture.

The most important properties are:

1. Reliable HTTP Range streaming
2. Failure isolation
3. Server independence from Flutter UI
4. Generic media-source support
5. Generic Android Share support
6. LAN/browser compatibility
7. Clean Kotlin/Dart boundary
8. No crashes from malformed user input or external applications

Build the project incrementally and keep it compiling after every phase.

Before implementing a new subsystem, inspect the existing project structure and integrate with it rather than replacing working components.

When something fails, fix the underlying architecture instead of adding a temporary workaround.