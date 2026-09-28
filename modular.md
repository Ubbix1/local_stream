You are refactoring the existing LocalStream project.

LocalStream is an Android application that turns an Android phone into a local media server.

The project currently contains:

- Flutter application/UI
- Native Android/Kotlin server
- Foreground service
- HTTP server
- HTTP Range media streaming
- WebSocket functionality where applicable
- NSD/mDNS discovery
- Media library
- Storage Access Framework
- Connected-device/client management
- Client website
- `/api/v1/*` API
- Browser media player
- VLC stream fallback
- Server metrics
- Diagnostics
- Telegram/backend telemetry integration

The goal is to reorganize the project into a:

DRY
MODULAR
TESTABLE
MAINTAINABLE
EXTENSIBLE

architecture.

IMPORTANT:

Do NOT perform a blind rewrite.

First understand the existing implementation and then refactor incrementally.

==================================================
1. CORE ARCHITECTURE
==================================================

Use clear architectural boundaries:

                    LocalStream
                        │
       ┌────────────────┼────────────────┐
       │                │                │
       ▼                ▼                ▼
   Flutter App     Android Server     Web Client
       │                │                │
       │                │                │
       └────────────────┼────────────────┘
                        │
                  API Contract
                        │
                        ▼
                   Media Domain

The systems must communicate through explicit interfaces/contracts.

Avoid direct dependencies between unrelated layers.

==================================================
2. HIGH-LEVEL MODULES
==================================================

Organize the project conceptually into:

APP
DOMAIN
DATA
SERVER
MEDIA
STORAGE
NETWORK
DISCOVERY
TELEMETRY
WEB

Suggested architecture:

lib/
├── app/
│   ├── app.dart
│   ├── router/
│   ├── theme/
│   └── di/
│
├── core/
│   ├── errors/
│   ├── result/
│   ├── logging/
│   ├── constants/
│   ├── utils/
│   └── extensions/
│
├── domain/
│   ├── media/
│   ├── server/
│   ├── client/
│   ├── network/
│   └── settings/
│
├── data/
│   ├── repositories/
│   ├── datasources/
│   ├── models/
│   └── mappers/
│
├── features/
│   ├── home/
│   ├── library/
│   ├── server/
│   ├── settings/
│   └── clients/
│
└── services/
    ├── server/
    ├── discovery/
    ├── network/
    ├── storage/
    └── telemetry/

Native Android:

android/
└── app/
    └── src/main/kotlin/.../
        ├── server/
        ├── streaming/
        ├── network/
        ├── discovery/
        ├── storage/
        ├── service/
        ├── telemetry/
        └── bridge/

Web client:

web/
├── src/
│   ├── api/
│   ├── player/
│   ├── media/
│   ├── connection/
│   ├── components/
│   ├── state/
│   └── utils/
├── styles/
└── index.html

IMPORTANT:

Adapt this structure to the existing project.

Do not force a new framework.

If the web client is currently plain HTML/CSS/JS, do not migrate to React/Vue/etc. merely for architecture.

==================================================
3. DOMAIN LAYER
==================================================

The domain layer must contain business concepts, not Flutter widgets and not HTTP implementation details.

Examples:

MediaItem
ServerStatus
ServerState
ConnectedClient
NetworkAddress
StreamInfo
MediaTrack
ServerMetrics
AppSettings

Example:

class MediaItem {
  final String id;
  final String name;
  final MediaType type;
  final int size;
}

The domain model should not know:

- Flutter
- HTTP
- JSON
- Android Context
- Kotlin
- SharedPreferences
- Widget state

==================================================
4. REPOSITORY PATTERN
==================================================

Use interfaces for important application capabilities.

Example:

abstract interface class MediaRepository {
  Future<List<MediaItem>> getMedia();
  Future<MediaItem?> getMediaById(String id);
  Future<void> removeMedia(String id);
}

Implementation:

class MediaRepositoryImpl implements MediaRepository {
  ...
}

UI depends on:

MediaRepository

not:

HttpClient
StorageAccessFramework
KotlinService
etc.

==================================================
5. SERVER ABSTRACTION
==================================================

Create a single server abstraction.

Example:

abstract interface class ServerController {

  Stream<ServerState> get state;

  Future<void> start();

  Future<void> stop();

  Future<void> restart();

  Future<ServerStatus> getStatus();

  Future<ServerAddress> getAddress();
}

Flutter should communicate with:

ServerController

rather than directly calling native Android implementation everywhere.

==================================================
6. NATIVE BRIDGE
==================================================

If Flutter communicates with Kotlin through:

MethodChannel
EventChannel
Pigeon
or another bridge,

centralize it.

Do NOT scatter:

MethodChannel(...)

throughout the Flutter application.

Create something like:

NativeServerDataSource

or:

AndroidServerBridge

Example:

class AndroidServerBridge {
  Future<void> startServer();
  Future<void> stopServer();
  Future<ServerStatus> getStatus();

  Stream<ServerEvent> events();
}

Only this module knows the native channel names.

==================================================
7. EVENT MODEL
==================================================

Use typed server events instead of random strings.

Bad:

"server_started"
"serverStarted"
"STARTED"

throughout the codebase.

Use:

enum ServerEventType {
  started,
  stopped,
  error,
  networkChanged,
  clientConnected,
  clientDisconnected,
  streamStarted,
  streamEnded,
}

Prefer strongly typed objects where practical.

==================================================
8. SERVER STATE MACHINE
==================================================

Create ONE authoritative server state.

Example:

enum ServerState {
  stopped,
  starting,
  running,
  stopping,
  error,
  networkUnavailable,
}

Do not duplicate server state across:

Home
Server page
Settings
Foreground service
Native server
notifications

The native server/service remains authoritative.

Flutter observes it.

==================================================
9. SERVER INTERNAL ARCHITECTURE
==================================================

Do NOT create one giant:

Server.kt

containing everything.

Separate responsibilities:

server/
├── HttpServer.kt
├── ServerController.kt
├── ServerConfig.kt
├── ServerState.kt
└── ServerLifecycle.kt

http/
├── Router.kt
├── RequestHandler.kt
├── Response.kt
├── HttpError.kt
└── routes/

routes/
├── RootRoute.kt
├── InfoRoute.kt
├── StatusRoute.kt
├── FilesRoute.kt
├── StreamRoute.kt
└── WatchRoute.kt

Each route should be small.

==================================================
10. ROUTING
==================================================

Do not repeat endpoint parsing everywhere.

Centralize:

method
path
parameters
status codes
content types

Example conceptual architecture:

Router
   │
   ├── GET /
   ├── GET /watch/:id
   ├── GET /api/v1/info
   ├── GET /api/v1/status
   ├── GET /api/v1/files
   ├── GET /api/v1/files/:id
   ├── GET /api/v1/stream/:id
   └── HEAD /api/v1/stream/:id

Routes should call application/domain services.

They should NOT directly contain large business logic.

==================================================
11. MEDIA SERVICE
==================================================

Create a dedicated MediaService.

Responsibilities:

- discover media
- metadata
- media lookup
- media validation
- media type detection
- media track information

Example:

MediaService

    getAllMedia()
    getMedia(id)
    getMetadata(id)
    getTracks(id)

The HTTP layer calls MediaService.

The Flutter library also accesses the appropriate repository/service.

==================================================
12. STREAMING SERVICE
==================================================

Create a dedicated StreamingService.

Responsibilities:

- open media
- parse Range
- calculate ranges
- create HTTP 206 response
- stream bytes
- handle disconnect
- cleanup resources
- metrics

Do NOT put this inside the route handler.

Architecture:

StreamRoute
     ↓
StreamingService
     ↓
MediaRepository
     ↓
File / Storage

==================================================
13. RANGE HANDLING
==================================================

Create a dedicated component:

RangeParser

or:

HttpRange

It should handle:

bytes=0-
bytes=0-1024
bytes=1024-
invalid ranges
multiple ranges if supported

Do not duplicate range calculations across routes.

==================================================
14. MEDIA CODEC DETECTION
==================================================

Create a separate:

MediaProbeService

Responsibilities:

- container detection
- video codec
- audio codec
- tracks
- duration
- MIME information
- browser compatibility metadata where practical

The StreamingService should NOT contain codec-detection code.

Example:

MediaProbeService
        ↓
MediaMetadata
        ↓
PlaybackDecisionService

==================================================
15. BROWSER PLAYBACK DECISION
==================================================

Create:

PlaybackStrategy

or:

PlaybackDecisionService

Possible strategies:

DirectStream
AudioTranscode
Remux
Unsupported

Example:

PlaybackDecision decide(
    MediaMetadata media,
    BrowserCapabilities browser
)

This keeps codec logic out of the HTTP routes.

==================================================
16. VLC FALLBACK
==================================================

VLC URL generation should be isolated.

Create:

VlcLinkService

or:

StreamUrlBuilder

Do not generate VLC URLs manually in multiple places.

Example:

StreamUrlBuilder.build(mediaId)

Used by:

Flutter
Web client
share functionality

where appropriate.

==================================================
17. URL BUILDING
==================================================

Never manually construct URLs everywhere.

Bad:

"http://" + ip + ":" + port + "/api/v1/stream/" + id

Create:

ServerUrlBuilder

Example:

serverUrl.stream(mediaId)

serverUrl.client()

serverUrl.info()

serverUrl.status()

serverUrl.files()

serverUrl.watch(mediaId)

This prevents endpoint duplication.

==================================================
18. API CONTRACT
==================================================

Treat `/api/v1` as a formal contract.

Create documented response models.

Example:

ServerInfoResponse
ServerStatusResponse
MediaListResponse
MediaMetadataResponse
StreamMetadataResponse

Do not make the web client depend on undocumented JSON fields.

==================================================
19. API VERSIONING
==================================================

Keep:

/api/v1

Do not randomly change existing fields.

If a breaking change is required:

/api/v2

Do not silently break the client website.

==================================================
20. JSON SERIALIZATION
==================================================

Centralize serialization.

Avoid:

json["name"]
json["id"]
json["codec"]

being scattered throughout the application.

Use typed models:

MediaDto
ServerStatusDto
ClientDto

Then map:

DTO
 ↓
Domain model

==================================================
21. STORAGE MODULE
==================================================

Storage Access Framework logic belongs in one module.

Example:

StorageDataSource

Responsibilities:

- SAF URI
- permissions
- file metadata
- stream opening
- URI persistence

The server should not contain random Android storage calls everywhere.

==================================================
22. FILE SYSTEM ABSTRACTION
==================================================

Create an abstraction such as:

MediaStorage

Methods:

open(id)
exists(id)
metadata(id)
delete(id)
list()

The HTTP server should not know whether the media is:

SAF
local filesystem
content URI
other storage.

==================================================
23. NSD / MDNS
==================================================

Isolate discovery.

Create:

DiscoveryService

Responsibilities:

register
unregister
status
service name
service type

The server should not contain NSD implementation details.

==================================================
24. NETWORK SERVICE
==================================================

Create:

NetworkService

Responsibilities:

- current IP
- interfaces
- Wi-Fi state
- network changes
- address updates

Do not duplicate IP detection in:

Home
Server page
NSD
foreground service

==================================================
25. FOREGROUND SERVICE
==================================================

The foreground service should be responsible for:

- keeping server alive
- Android notification
- lifecycle
- native server ownership

It should NOT contain:

Flutter UI logic
media library UI
HTTP route logic
business logic unrelated to lifecycle

Keep it thin.

==================================================
26. TELEMETRY
==================================================

Create a separate:

TelemetryService

The telemetry module should have no dependency on:

Flutter widgets
HTTP routes
media player
client website

Only send approved fields:

Device model
Android version
Battery level
Charging state
App version

Keep Telegram/backend credentials outside the client website.

==================================================
27. SERVER METRICS
==================================================

Create:

MetricsService

Track:

requests
connections
active streams
bytes served
errors
uptime

Do not let random classes increment metrics independently.

Use one metrics interface.

==================================================
28. LOGGING
==================================================

Create:

Logger

with categories:

server
http
stream
network
storage
discovery
telemetry
error

Avoid:

print()

everywhere.

Use:

logger.info(...)
logger.warning(...)
logger.error(...)

Do not log secrets.

==================================================
29. ERROR MODEL
==================================================

Create centralized typed errors.

Example:

ServerError
NetworkError
StorageError
MediaError
StreamingError
PlaybackError

Use a consistent Result/error strategy.

Avoid random:

try {
 ...
} catch (e) {
 print(e);
}

throughout the project.

==================================================
30. DRY RULE
==================================================

Before creating new code, ask:

"Does this logic already exist?"

Avoid duplicate:

URL construction
IP detection
server status
media metadata
range parsing
MIME detection
error formatting
logging
button styling
device formatting
file-size formatting

Create reusable utilities/services only when the abstraction is genuinely shared.

Do NOT create meaningless abstractions just to increase file count.

==================================================
31. UI COMPONENTS
==================================================

Create reusable UI components.

Example:

widgets/
├── glass/
│   ├── LiquidGlassButton.dart
│   └── LiquidGlassIconButton.dart
│
├── server/
│   ├── ServerStatusCard.dart
│   ├── ServerAddressCard.dart
│   ├── ServerMetricsGrid.dart
│   └── ConnectedClientCard.dart
│
├── media/
│   ├── MediaCard.dart
│   ├── MediaFilterBar.dart
│   └── MediaSearchBar.dart
│
└── common/
    ├── LoadingView.dart
    ├── ErrorView.dart
    └── EmptyView.dart

Avoid duplicating UI patterns.

==================================================
32. LIQUID GLASS
==================================================

Create ONE reusable implementation.

For example:

LiquidGlassButton

Do not implement the Liquid Glass effect separately in:

Home
Library
Server
Settings

All buttons use the shared component.

Important:

Liquid Glass is ONLY for buttons.

==================================================
33. THEME
==================================================

Create one theme system.

Centralize:

colors
text styles
spacing
radii
button sizes
shadows
icons

Do not hardcode:

Color(...)
EdgeInsets(...)
BorderRadius(...)
TextStyle(...)

everywhere.

Use:

AppColors
AppSpacing
AppRadius
AppTypography

where appropriate.

==================================================
34. FEATURE MODULES
==================================================

Each feature should own its:

UI
state
controller/view-model
feature-specific widgets

Example:

features/home/

home_page.dart
home_controller.dart
widgets/

features/library/

library_page.dart
library_controller.dart
widgets/

features/server/

server_page.dart
server_controller.dart
widgets/

features/settings/

settings_page.dart
settings_controller.dart
widgets/

Features should depend on domain/repositories, not each other directly.

==================================================
35. STATE MANAGEMENT
==================================================

Inspect the existing state-management solution.

Do not introduce another state-management framework unless necessary.

If using:

Riverpod
Provider
Bloc
Cubit
ChangeNotifier

keep one consistent approach.

Do not mix five different patterns.

The architecture should make state ownership obvious.

==================================================
36. DEPENDENCY INJECTION
==================================================

Create one dependency graph.

Example:

App
 ↓
Repositories
 ↓
Services
 ↓
Data sources

Do not instantiate services randomly inside widgets.

Bad:

Widget
 ↓
new MediaService()

Prefer:

Widget
 ↓
Controller
 ↓
Repository
 ↓
Service

==================================================
37. WEB CLIENT ARCHITECTURE
==================================================

The browser client should also be modular.

Example:

web/
├── api/
│   ├── client.js
│   ├── mediaApi.js
│   └── serverApi.js
│
├── player/
│   ├── player.js
│   ├── controls.js
│   ├── playbackStrategy.js
│   └── codecSupport.js
│
├── media/
│   ├── mediaList.js
│   ├── mediaCard.js
│   └── search.js
│
├── connection/
│   ├── connectionManager.js
│   └── reconnect.js
│
├── components/
│   ├── buttons.js
│   ├── dialogs.js
│   └── loading.js
│
└── app.js

Do not put the entire website into one huge app.js.

==================================================
38. WEB API CLIENT
==================================================

Create one API client.

Example:

api.getInfo()
api.getStatus()
api.getFiles()
api.getFile(id)
api.getStreamUrl(id)

Do not scatter:

fetch("/api/v1/...")

through every component.

==================================================
39. WEB VIDEO PLAYER
==================================================

Keep player responsibilities separate:

VideoPlayer
PlaybackState
PlaybackStrategy
CodecSupport
PlayerControls
StreamUrl

The player should not know how the Android server internally stores files.

==================================================
40. CENTER PLAY BUTTON
==================================================

The center Play button is a UI component.

Its state must depend on the player state.

PAUSED:

show

PLAYING:

hide

ENDED:

show

Do not mix this logic into API code.

==================================================
41. BROWSER AUDIO CODEC
==================================================

Codec handling must remain modular.

Example:

BrowserCapabilities
       ↓
MediaMetadata
       ↓
PlaybackDecision
       ↓
DirectStream
or
CompatibilityStream
or
VlcFallback

Do not put codec detection into HTML event handlers.

==================================================
42. CONFIGURATION
==================================================

Centralize:

API version
default port
service name
service type
timeouts
limits

Example:

AppConfig

Do not duplicate:

8080
/api/v1
LocalStream
_http._tcp

throughout the code.

==================================================
43. CONSTANTS
==================================================

Only create constants for genuinely shared values.

Examples:

DEFAULT_PORT
API_VERSION
SERVICE_NAME
SERVICE_TYPE

Avoid a giant Constants.dart containing unrelated values.

Prefer domain-specific constants.

==================================================
44. SECURITY BOUNDARY
==================================================

Keep these completely outside frontend code:

Telegram bot token
private API keys
authentication secrets
internal filesystem paths
private telemetry

Never expose them through:

Flutter UI
client website
JavaScript
public API response

==================================================
45. TESTING ARCHITECTURE
==================================================

Every major module should be independently testable.

Unit tests:

RangeParser
MediaProbeService
PlaybackDecisionService
UrlBuilder
ServerState
NetworkAddress
MetricsService

Integration tests:

HTTP server
API routes
Range streaming
media lookup
NSD where testable

Flutter tests:

ServerController
LibraryController
HomeController
widgets

Web tests:

API client
player state
codec decision
connection manager

==================================================
46. DEPENDENCY RULES
==================================================

Use this direction:

UI
 ↓
Controller / ViewModel
 ↓
Repository / Use Case
 ↓
Service
 ↓
Data Source

Not:

UI
 ↓
HTTP
 ↓
Kotlin
 ↓
Storage
 ↓
Random global state

Domain must remain independent.

==================================================
47. AVOID CIRCULAR DEPENDENCIES
==================================================

Never allow:

Home → Library → Home

Server → Flutter UI

Storage → Server UI

Web → Android internals

Instead:

Feature
 ↓
Domain
 ↓
Repository

==================================================
48. SINGLE RESPONSIBILITY
==================================================

Each class should have one clear reason to change.

Bad:

LocalStreamManager

containing:

HTTP
NSD
storage
streaming
telemetry
logging
UI state
metrics

Split those responsibilities.

==================================================
49. BUT DO NOT OVER-ENGINEER
==================================================

Do NOT turn every 10-line function into a class.

Good modularity means:

clear boundaries
low coupling
high cohesion
reusable behavior

NOT:

500 tiny files.

If two components are tightly coupled and always change together, keeping them together may be correct.

==================================================
50. REFACTORING PROCESS
==================================================

Do NOT refactor the entire project in one destructive operation.

Use phases.

PHASE 1
Audit existing architecture.

Create:

ARCHITECTURE.md

containing:

- current architecture
- dependencies
- server lifecycle
- API routes
- data flow
- streaming flow
- Flutter/native bridge
- web client architecture

PHASE 2

Extract shared models.

PHASE 3

Extract server abstractions.

PHASE 4

Extract media/storage services.

PHASE 5

Extract networking/discovery.

PHASE 6

Extract HTTP routes.

PHASE 7

Refactor Flutter features.

PHASE 8

Refactor web client.

PHASE 9

Centralize UI components/theme.

PHASE 10

Add tests.

PHASE 11

Remove dead/duplicate code.

PHASE 12

Performance and regression testing.

==================================================
51. BEFORE / AFTER REQUIREMENT
==================================================

Before modifying each module:

Understand:

who calls it
what it calls
what state it owns
what APIs depend on it
what UI depends on it

Then refactor.

After each phase:

flutter analyze
flutter test
Android build
server test
web client test

Do not continue if a major regression appears.

==================================================
52. DOCUMENTATION
==================================================

Create:

ARCHITECTURE.md
API.md
STREAMING.md
DEVELOPMENT.md

ARCHITECTURE.md:

system architecture
module boundaries
dependency direction

API.md:

/api/v1
request/response
status codes

STREAMING.md:

Range requests
stream lifecycle
codec handling
browser compatibility
VLC fallback

DEVELOPMENT.md:

build
test
debug
run server
run web client

==================================================
53. FINAL DIRECTORY TARGET
==================================================

Aim for a structure conceptually similar to:

LocalStream/
│
├── lib/
│   ├── app/
│   ├── core/
│   ├── domain/
│   ├── data/
│   ├── services/
│   └── features/
│
├── android/
│   └── app/src/main/kotlin/
│       └── .../
│           ├── server/
│           ├── http/
│           ├── streaming/
│           ├── media/
│           ├── storage/
│           ├── network/
│           ├── discovery/
│           ├── service/
│           ├── telemetry/
│           └── bridge/
│
├── web/
│   ├── src/
│   │   ├── api/
│   │   ├── player/
│   │   ├── media/
│   │   ├── connection/
│   │   └── components/
│   └── styles/
│
├── test/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── STREAMING.md
│   └── DEVELOPMENT.md
│
└── README.md

Adapt this to the existing repository instead of blindly creating duplicate folders.

==================================================
54. FINAL QUALITY CHECK
==================================================

Before finishing, inspect the codebase for:

Duplicate URL construction
Duplicate server state
Duplicate IP detection
Duplicate media metadata parsing
Duplicate error handling
Duplicate formatting
Duplicate button implementations
Duplicate API calls
Duplicate storage logic
Duplicate codec detection
Duplicate logging

Consolidate genuine duplication.

Then check for:

Circular dependencies
God classes
God services
UI → native implementation coupling
HTTP → storage coupling
Web → server internals
hardcoded configuration
secrets
dead code
unused dependencies

==================================================
55. MOST IMPORTANT RULE
==================================================

DRY does NOT mean:

"put everything into one generic helper."

Modular does NOT mean:

"create hundreds of tiny files."

The goal is:

HIGH COHESION
LOW COUPLING
CLEAR OWNERSHIP
ONE SOURCE OF TRUTH
EXPLICIT CONTRACTS
TESTABLE COMPONENTS
MINIMAL DUPLICATION

==================================================
56. DEFINITION OF DONE
==================================================

The refactor is complete only when:

[ ] Flutter UI does not directly control native server internals.
[ ] Native server lifecycle is independent from Flutter UI.
[ ] HTTP routes are separated from business logic.
[ ] Streaming is isolated from HTTP routing.
[ ] Media probing is isolated.
[ ] Storage access is isolated.
[ ] NSD is isolated.
[ ] Network detection is isolated.
[ ] Metrics are centralized.
[ ] Logging is centralized.
[ ] API models are typed.
[ ] API versioning is explicit.
[ ] URL construction is centralized.
[ ] Web API calls are centralized.
[ ] Browser player is modular.
[ ] Codec compatibility logic is isolated.
[ ] Liquid Glass buttons are reusable.
[ ] Theme values are centralized.
[ ] Connected-client state has one owner.
[ ] Server state has one authoritative source.
[ ] Telegram/private telemetry is isolated.
[ ] No secrets reach the client.
[ ] No major duplicated logic remains.
[ ] No circular dependencies.
[ ] Unit tests exist for core logic.
[ ] Integration tests cover HTTP/range streaming.
[ ] Flutter analyze passes.
[ ] Flutter tests pass.
[ ] Android build succeeds.
[ ] Web client works.
[ ] Existing media streaming still works.
[ ] VLC fallback still works.
[ ] Existing `/api/v1` contract remains compatible.

==================================================
FINAL PRINCIPLE
==================================================

Do not optimize the project for "more folders."

Optimize it for:

                 CLEAR RESPONSIBILITY

                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
       Flutter       Android          Web
          │           Server          Client
          │             │               │
          └─────────────┼───────────────┘
                        │
                 Explicit Contracts
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
       Media         Network        Storage
          │             │             │
          └─────────────┼─────────────┘
                        ▼
                    Streaming

Keep each responsibility in exactly one appropriate place.

Refactor incrementally.
Preserve behavior.
Test after every architectural change.
Do not rewrite working functionality without a concrete reason.