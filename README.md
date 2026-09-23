# LocalStream Backbone

Local-first Android media server with two Flutter clients and a browser client.

- `apps/mobile`: phone server/manager
- `apps/tv`: optional Android TV/Fire TV client
- `packages/protocol`: shared API models
- `packages/media_core`: source/repository abstractions
- `server/web`: browser UI served by the phone
- `docs`: architecture, API, failure isolation

Flutter/Dart handles UI, state and protocol. Kotlin handles Android platform services:
Foreground Service, Storage Access Framework, Android Share intents, NSD/mDNS,
HTTP server integration and Media3/ExoPlayer.

**Failure isolation:** a bad file, URI, request, client, storage device, discovery
failure or playback failure must not terminate unrelated components or the server.
Internal exceptions are logged; the normal UI receives only short actionable messages.
