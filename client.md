# Upgrade the Client Website Served by the Android Server APK

## Context

The existing application is an **Android APK that acts as the server**.

When the Android APK starts its server, it exposes a web application that clients can access through a browser over the network.

The website is therefore a **client-facing web interface**, NOT an administrative server dashboard.

The goal is to significantly upgrade the client website's UI/UX while preserving all existing server functionality, streaming behavior, APIs, and protocols.

---

# 1. Understand the Architecture First

Before modifying anything, inspect the complete Android/server project.

Understand:

```text
Android APK
     │
     ├── HTTP server
     ├── WebSocket server if present
     ├── REST/API endpoints
     ├── Video/file streaming
     ├── Authentication if present
     └── Static client website
             │
             ▼
          Browser
```

Find:

* Where the website files are stored.
* How the Android server serves the website.
* Existing HTML.
* CSS.
* JavaScript.
* API endpoints.
* WebSocket endpoints.
* Video streaming endpoints.
* File listing endpoints.
* Authentication/session handling.
* Existing player implementation.
* Existing client-server communication.

**Do not rewrite the Android server simply to redesign the website.**

---

# 2. Primary Goal

Upgrade the client website into a polished modern media/file client.

The website should feel like a real production application rather than a basic HTML interface.

Prioritize:

* clean UI
* responsive layout
* smooth interactions
* excellent video playback
* mobile compatibility
* desktop compatibility
* fast loading
* clear server connection status
* intuitive navigation.

---

# 3. Do NOT Break Existing Backend Behavior

The existing Android server is already responsible for:

```text
HTTP
WebSocket
Video streaming
File serving
Range requests
Authentication
Device communication
```

Do not change these systems unless absolutely necessary.

The website must adapt to the existing APIs.

Before changing an endpoint, inspect how the Android server currently handles it.

---

# 4. Client Website Layout

Create a modern media-client layout.

### Desktop

```text
┌─────────────────────────────────────────────────────────────┐
│ LOGO                         Search       ● Connected       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Home   Movies   Videos   Files   Recent                   │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                    MEDIA CONTENT                            │
│                                                             │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│   │          │  │          │  │          │                 │
│   │ Thumbnail│  │ Thumbnail│  │ Thumbnail│                 │
│   │          │  │          │  │          │                 │
│   └──────────┘  └──────────┘  └──────────┘                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Mobile

```text
┌────────────────────────────┐
│ ☰   APP NAME       ●       │
├────────────────────────────┤
│                            │
│ Search                     │
│                            │
│ ┌────────────────────────┐ │
│ │                        │ │
│ │       Thumbnail        │ │
│ │                        │ │
│ └────────────────────────┘ │
│                            │
│ Movie / Video Name         │
│                            │
│ ┌────────────────────────┐ │
│ │                        │ │
│ │       Thumbnail        │ │
│ │                        │ │
│ └────────────────────────┘ │
└────────────────────────────┘
```

Adapt the actual layout to the application's existing features.

---

# 5. Video Player

The video player is one of the most important parts of the website.

Preserve the existing working streaming endpoint.

Support:

```text
Play
Pause
Seek
Volume
Mute
Fullscreen
Playback progress
Duration
Loading state
Buffering state
Error state
```

If the server supports HTTP Range requests, ensure the frontend player uses the existing streaming URL correctly.

Do NOT unnecessarily download the entire video before playback.

Prefer native browser streaming behavior.

---

# 6. Audio Compatibility

The current project may have cases where:

```text
Video works
Audio does not
```

Do not assume that the browser supports every codec/container combination.

Inspect the existing media pipeline.

The client should:

1. Attempt normal browser playback.
2. Detect playback errors.
3. Display a useful error.
4. Avoid crashing.
5. Preserve the existing stream URL.
6. Support the formats/codecs that browsers actually support.

Example:

```text
Unable to play this media in your browser.

The video stream may use an audio codec
that this browser does not support.
```

Do not silently fail.

---

# 7. Media Cards

Create modern media cards.

Each card can contain:

```text
Thumbnail
Title
Duration
File size
Media type
```

Example:

```text
┌──────────────────────────┐
│                          │
│       THUMBNAIL          │
│                          │
│                    01:42 │
├──────────────────────────┤
│ Interstellar             │
│ 1080p • MP4              │
└──────────────────────────┘
```

Use the metadata that the existing server actually provides.

Do not invent metadata.

---

# 8. File Browser

If the existing client supports files, create a clean file browser.

Support:

```text
Folders
Videos
Images
Audio
Documents
Other files
```

Use appropriate icons.

Provide:

```text
Open
Play
Download
Back
Sort
Search
```

where supported by the existing backend.

---

# 9. Search

Add client-side or server-backed search depending on the existing API.

Search should support:

```text
filename
title
folder
media type
```

Do not introduce expensive server queries if the dataset is small enough for client-side filtering.

---

# 10. Connection Status

The client website should clearly show whether it can communicate with the Android server.

Example:

```text
● Connected
```

or:

```text
○ Reconnecting...
```

or:

```text
× Server unavailable
```

Use the existing API/WebSocket heartbeat where available.

Do not expose internal server diagnostics.

---

# 11. Reconnection

If the browser loses connection:

```text
Connected
    ↓
Connection lost
    ↓
Reconnecting...
    ↓
Connected
```

Do not refresh the entire page.

Do not lose the current UI state unnecessarily.

Use exponential backoff for WebSocket reconnection if WebSockets are used.

---

# 12. Loading States

Every asynchronous operation should have a proper loading state.

Examples:

```text
Loading media...
Connecting to server...
Loading folder...
Opening video...
```

Use skeleton loaders where appropriate.

Avoid showing blank screens.

---

# 13. Error Handling

The website must never expose raw JavaScript exceptions or backend stack traces.

Use friendly messages:

```text
Unable to connect to the server.

Check that the Android server is running
and that your device is connected to the same network.
```

For media errors:

```text
Unable to play this video.

Try another browser or media file.
```

Provide a retry action where appropriate.

---

# 14. Liquid Glass Buttons

The existing requirement is:

> **Liquid Glass is for buttons only.**

Use the Liquid Glass package/style only for actual interactive buttons.

Examples:

```text
▶ Play
⬇ Download
↻ Retry
⛶ Fullscreen
← Back
```

Do NOT turn the entire website into Liquid Glass.

Do NOT apply it to:

```text
Background
Cards
Media thumbnails
Navigation
Entire player
Every container
```

The overall website should remain clean and modern.

---

# 15. Visual Design

Use a premium media-player aesthetic.

Suggested characteristics:

* dark-first interface if appropriate for the existing application
* strong media thumbnails
* subtle borders
* clean typography
* rounded cards
* smooth transitions
* clear hierarchy
* responsive spacing
* minimal visual clutter.

Avoid excessive gradients and excessive glass effects.

The media itself should remain the visual focus.

---

# 16. Responsive Design

The client website must work properly on:

### Mobile

```text
360px+
```

### Tablet

```text
768px+
```

### Desktop

```text
1024px+
```

### Large desktop

```text
1440px+
```

Do not allow:

```text
horizontal overflow
broken video controls
tiny buttons
overlapping cards
```

---

# 17. Touch-Friendly Controls

On mobile:

* buttons must have adequate touch targets
* video controls must be easy to use
* cards must be easy to tap
* fullscreen must work
* navigation must not require hover.

Do not rely exclusively on mouse hover interactions.

---

# 18. Video Player Mobile UX

On mobile, the player should support:

```text
Tap → show/hide controls
Double tap → optional seek
Fullscreen
Landscape playback where supported
Volume/mute
Seek bar
```

Implement only interactions that are reliable across supported browsers.

Do not over-engineer gestures.

---

# 19. Theme

Support the existing application's theme.

If the current website is dark-themed, preserve that direction.

If theme switching already exists:

```text
Light
Dark
System
```

ensure the upgraded UI works correctly with it.

Do not change the entire application's branding without reason.

---

# 20. Accessibility

Ensure:

* buttons have accessible labels
* keyboard navigation works
* focus states are visible
* sufficient contrast
* media controls are accessible
* images have appropriate alt text where necessary.

Icon-only buttons must have accessible labels.

Example:

```html
<button aria-label="Fullscreen">
```

---

# 21. Performance

The website may be running directly from an Android device/server.

Keep it lightweight.

Avoid:

* huge JavaScript bundles
* unnecessary libraries
* excessive animations
* unnecessary network requests
* large background images
* expensive blur everywhere.

Optimize:

```text
CSS
JavaScript
Images
Thumbnails
API requests
```

The client should load quickly even over a local Wi-Fi connection.

---

# 22. Preserve Streaming Performance

This is especially important.

Do NOT introduce frontend logic that causes the video to:

```text
download completely
→ store in memory
→ convert
→ then play
```

Use the server's existing stream URL directly whenever possible.

For example:

```html
<video
    src="/api/video/..."
    controls>
</video>
```

or the appropriate existing endpoint.

Preserve:

```text
HTTP Range
206 Partial Content
Content-Length
Content-Type
Accept-Ranges
```

behavior provided by the Android server.

---

# 23. Security

Do not expose:

```text
server secrets
Telegram credentials
device authentication tokens
internal API credentials
```

in frontend source.

Do not hardcode secrets in:

```text
HTML
CSS
JavaScript
localStorage
```

If authentication/session information is required, use the existing secure mechanism.

---

# 24. Browser Compatibility

Test the client in:

```text
Chrome
Edge
Firefox
Safari where applicable
Android Chrome
iOS Safari where applicable
```

Pay particular attention to:

```text
video
audio
fullscreen
HTTP Range
WebSocket
autoplay restrictions
```

---

# 25. Project Structure

Follow the existing project structure.

If appropriate, organize the client as:

```text
client/
├── index.html
├── assets/
├── css/
│   ├── app.css
│   ├── player.css
│   └── responsive.css
├── js/
│   ├── app.js
│   ├── player.js
│   ├── api.js
│   └── connection.js
└── components/
```

If the project already uses React/Vue/etc., keep that framework instead.

Do not migrate frameworks just for visual redesign.

---

# 26. Preserve Existing APIs

Before changing the frontend, create an inventory of existing endpoints.

Example:

```text
GET  /
GET  /api/files
GET  /api/files/...
GET  /api/video/...
GET  /api/health
WS   /ws
```

Use the actual project's endpoints.

Do not invent replacements when the existing API already works.

---

# 27. Final Testing

After implementation verify:

### Website

* [ ] Loads when Android server starts.
* [ ] Works on local network.
* [ ] Responsive mobile layout.
* [ ] Responsive desktop layout.
* [ ] Navigation works.
* [ ] Search works.
* [ ] File browsing works.
* [ ] Media cards work.
* [ ] Video playback works.
* [ ] Audio playback works where browser-supported.
* [ ] Range streaming still works.
* [ ] Fullscreen works.
* [ ] Connection indicator works.
* [ ] Reconnection works.
* [ ] Error states work.
* [ ] Liquid Glass is used only on buttons.

### Android server

* [ ] Existing HTTP server remains functional.
* [ ] Existing WebSocket functionality remains functional.
* [ ] Existing APIs remain compatible.
* [ ] Streaming performance is unchanged or improved.
* [ ] No unnecessary server changes were introduced.

---

# Most Important Rules

### Rule 1

**The Android APK is the server.**

### Rule 2

**The website is the client.**

### Rule 3

**Upgrade the client UI, not the server architecture.**

### Rule 4

**Preserve the existing streaming implementation and HTTP Range behavior.**

### Rule 5

**Liquid Glass is for buttons only.**

### Rule 6

**Do not expose secrets or internal server information to the browser.**

### Rule 7

**Do not sacrifice video/audio playback performance for visual effects.**

### Final objective

Turn the existing browser client into a polished, responsive **local media/file client** that feels like a modern application while remaining lightweight enough to be served directly from the Android APK.

```text
                    ANDROID APK
                  ┌──────────────┐
                  │ HTTP SERVER   │
                  │ WebSocket     │
                  │ Media Stream  │
                  │ File Server   │
                  └───────┬──────┘
                          │
                     Local Network
                          │
                          ▼
              ┌───────────────────────┐
              │     CLIENT WEBSITE   │
              │                       │
              │  🏠 Home              │
              │  🎬 Media             │
              │  📁 Files             │
              │  🔎 Search            │
              │                       │
              │  ┌─────────────────┐ │
              │  │   VIDEO PLAYER  │ │
              │  │                 │ │
              │  │      ▶          │ │
              │  │  ─────────────  │ │
              │  └─────────────────┘ │
              │                       │
              │  Liquid Glass buttons │
              │  only                 │
              └───────────────────────┘
```
