# LocalStream Media Streaming Specification

This document provides the definitive technical specification and operational guide for media streaming in LocalStream. It covers HTTP Range request mechanics, byte stream lifecycle, browser codec compatibility, VLC fallback, and verification diagnostics.

---

## 1. Streaming Architecture Overview

LocalStream implements stateless HTTP Range media streaming. Rather than transcoding on the fly or maintaining stateful movie sessions, the server treats each media stream as an independent byte-range request directly backed by the Android device's file system or Content Provider.

```text
 ┌───────────────────────────────┐
 │   Android Media Source        │  (SAF DocumentFile, Shared content:// URI,
 │   (File / Content Provider)   │   or Internal App Storage)
 └──────────────┬────────────────┘
                │
                │ source.readRange(start, end)
                ▼
 ┌───────────────────────────────┐
 │      BoundedInputStream       │  Enforces byte boundary & offsets
 └──────────────┬────────────────┘
                │
                │ 64 KB chunked transfer
                ▼
 ┌───────────────────────────────┐
 │   StreamingResponseWriter     │  Appends HTTP headers (206/200/416)
 └──────────────┬────────────────┘
                │
                │ TCP Socket (soTimeout = 30s)
                ▼
 ┌───────────────────────────────┐
 │        Wi-Fi / Hotspot        │  Local Area Network
 └──────────────┬────────────────┘
                │
                ▼
 ┌───────────────────────────────┐
 │      Browser Video Player     │  HTML5 <video> / MSE / Range buffering
 │   or VLC / TV Client App      │
 └───────────────────────────────┘
```

### Core Design Principles
1. **Stateless Requests**: The server does not maintain seek cursors or playback session states. When a user seeks 45 minutes into a movie, the video player aborts the current TCP connection and issues a new HTTP Range request.
2. **Resource Isolation**: Each streaming request executes in an isolated coroutine within an IO thread pool, bounded by a `Semaphore(32)` to prevent resource exhaustion.
3. **Graceful Teardown**: When a client pauses or disconnects, the socket closes, throwing a handled `SocketException`. The input stream is closed immediately in a `finally` block, releasing memory and file descriptors.

---

## 2. HTTP Range Request Mechanics

All streaming endpoints (`GET /api/v1/stream/:id`) strictly adhere to [RFC 7233 (HTTP Range Requests)](https://datatracker.ietf.org/doc/html/rfc7233).

### 2.1 Range Format Handling

The server parses incoming `Range` headers using `RangeRequestParser`:

| Request Header | Classification | Server Response |
| :--- | :--- | :--- |
| `Range: bytes=0-1048575` | **Bounded** | Transmits exactly bytes `0` to `1048575` inclusive (1 MB). |
| `Range: bytes=1048576-` | **Open-ended** | Transmits from byte `1048576` to the final byte (`fileSize - 1`). |
| `Range: bytes=-524288` | **Suffix** | Transmits the final `524288` bytes of the file. |
| _Header omitted_ | **Full** | Returns `200 OK` and streams from byte `0` to end of file. |

#### EOF Clamping Rule
If a client requests a range extending beyond the end of the file (e.g., `bytes=5000000-999999999999` for a 10 MB file), the server automatically clamps the range to `totalSize - 1` and returns `206 Partial Content`.

#### Unsatisfiable Ranges
If `start >= totalSize` or `start > end`, the server responds with:
```http
HTTP/1.1 416 Range Not Satisfiable
Content-Type: application/json; charset=utf-8
Content-Range: bytes */<totalSize>
Content-Length: <errorJsonLength>
```

### 2.2 Response Headers

#### Successful 206 Partial Content Response
```http
HTTP/1.1 206 Partial Content
Content-Type: video/mp4
Content-Length: 1048576
Content-Range: bytes 0-1048575/2147483648
Accept-Ranges: bytes
Access-Control-Allow-Origin: *
Access-Control-Allow-Headers: Range, Content-Type, Accept
Access-Control-Expose-Headers: Content-Range, Content-Length, Accept-Ranges, X-LocalStream-Protocol-Version
X-LocalStream-Protocol-Version: 1
Content-Disposition: inline; filename="movie.mp4"; filename*=UTF-8''movie.mp4
Connection: close
```

---

## 3. Codec Compatibility & Browser Playback

Modern desktop and mobile web browsers natively support a subset of media containers and codecs.

### 3.1 Browser Support Matrix

| Container / Format | Video Codec | Audio Codec | Native Browser Support | Recommended Action |
| :--- | :--- | :--- | :--- | :--- |
| **MP4 (`.mp4`, `.m4v`)** | H.264 (AVC) | AAC / MP3 | **Universal (100%)** | Direct HTML5 Playback |
| **MP4 (`.mp4`)** | H.265 (HEVC) | AAC | Safari, Edge, Chrome (HW) | Direct Playback if supported |
| **WebM (`.webm`)** | VP8 / VP9 / AV1 | Opus / Vorbis | **Universal (Modern)** | Direct HTML5 Playback |
| **MKV (`.mkv`)** | H.264 / H.265 | AAC | Partial (Depends on browser) | Try Playback, fallback to VLC |
| **MKV (`.mkv`)** | Any | **AC-3 / E-AC-3 (Dolby)** | **Unsupported** in most browsers | Launch in VLC (Audio will fail in browser) |
| **MKV (`.mkv`)** | Any | **DTS / DTS-HD** | **Unsupported** in browsers | Launch in VLC |
| **AVI (`.avi`)** | Xvid / DivX | MP3 / AC-3 | **Unsupported** | Launch in VLC |

### 3.2 Client-Side Codec Detection & Audio Warnings
The embedded web player inspects media metadata and container characteristics. If an item contains incompatible audio streams (e.g. multi-channel Dolby or DTS):
1. A warning banner is displayed at the top of the video stage.
2. The user is provided an explicit action: *"Browser may not decode this audio track. Open stream in VLC"*.

---

## 4. VLC Stream Fallback & External Players

For media files with unsupported codecs, high-bitrate 4K HDR profiles, or external subtitle requirements, LocalStream provides immediate integration with external media players such as VLC, MPV, or Kodi.

### 4.1 URL Construction & Authentication Bypass
Standard desktop and TV external players cannot submit custom authentication headers (`X-LocalStream-Token`) or participate in browser cookie jars. LocalStream supports a query-parameter authentication token:
```text
http://<PHONE_IP>:<PORT>/api/v1/stream/<MEDIA_ID>?token=<SESSION_TOKEN>
```

### 4.2 One-Click Deep Linking
The web client exposes two fallback actions:
1. **Copy Stream URL**: Places the authenticated direct HTTP stream URL onto the clipboard for easy pasting into VLC (`Media -> Open Network Stream`).
2. **Open in VLC Protocol (`vlc://`)**:
   ```text
   vlc://http://192.168.1.105:8080/api/v1/stream/tree:doc-1234?token=7f8a9b...
   ```
   If VLC is installed on the user's desktop or mobile device, the browser automatically routes the stream to the VLC native application.

---

## 5. Verification Workflow & Diagnostic Signatures

To ensure optimal streaming performance and diagnose playback stutter or buffering, use the following verification procedures.

### 5.1 Command-Line Verification with `curl`

Verify Range requests directly against the phone's IP address:

```powershell
# 1. Bounded Range (First 1 MB)
curl.exe -i -H "Range: bytes=0-1048575" http://PHONE_IP:PORT/api/v1/stream/MEDIA_ID

# 2. Open-ended Range (Seeking halfway into a 100 MB file)
curl.exe -i -H "Range: bytes=52428800-" http://PHONE_IP:PORT/api/v1/stream/MEDIA_ID

# 3. Suffix Range (Last 512 KB)
curl.exe -i -H "Range: bytes=-524288" http://PHONE_IP:PORT/api/v1/stream/MEDIA_ID

# 4. Out-of-bounds Range (Expecting 416 Range Not Satisfiable)
curl.exe -i -H "Range: bytes=999999999999-" http://PHONE_IP:PORT/api/v1/stream/MEDIA_ID
```

Expected verification checklist:
- [x] Status code is `206 Partial Content`.
- [x] `Accept-Ranges: bytes` is present in headers.
- [x] `Content-Range` reports actual inclusive start, end, and total byte count.
- [x] `Content-Length` matches `end - start + 1`.
- [x] Out-of-bounds requests return `416 Range Not Satisfiable` with `Content-Range: bytes */<total>`.

### 5.2 Real-Time Diagnostic HUD

The web player (`/watch/:id`) features a real-time diagnostics overlay displaying five critical streaming metrics:

1. **Current Throughput**: Instantaneous bytes received per second across the local network.
2. **Movie Bitrate**: Calculated as `fileSize / duration`.
3. **Buffer Health**: Seconds of forward media buffer currently resident in browser memory ahead of the playback playhead.
4. **Presentation FPS**: Measured presented frames per second using `HTMLVideoElement.requestVideoFrameCallback`.
5. **Dropped Frames**: Cumulative dropped video frames reported by `getVideoPlaybackQuality()`.

### 5.3 Diagnostic Signatures

#### Healthy Playback
```text
Status: GOOD
Throughput:    45 Mbps
Movie Bitrate: 14 Mbps (Throughput >> Bitrate)
Buffer Ahead:  24.5 sec
FPS:           23.98 / 24 fps
Dropped:       0
Verdict:       Local network and hardware decoders are fully optimal.
```

#### Network Bottleneck
```text
Status: BUFFERING RISK / INSUFFICIENT BANDWIDTH
Throughput:    6.2 Mbps
Movie Bitrate: 18.0 Mbps (Throughput < Bitrate)
Buffer Ahead:  0.3 sec
FPS:           24 fps (intermittent freeze)
Dropped:       0
Verdict:       Weak Wi-Fi signal or 2.4 GHz channel interference. Move phone closer to router or use 5 GHz Wi-Fi hotspot.
```

#### Decoder / Hardware Bottleneck
```text
Status: GOOD (Network) / DECODER STALL
Throughput:    40 Mbps
Movie Bitrate: 12 Mbps
Buffer Ahead:  30 sec (Buffer full)
FPS:           9 fps
Dropped:       1,820 frames
Verdict:       Browser software decoding cannot keep up with high-profile video format. Switch to external player (VLC).
```
