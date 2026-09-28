# LocalStream API Specification (v1)

This document is the definitive reference for the LocalStream HTTP API exposed by the Android native server on port `8080` (or the dynamically bound port reported by mDNS and status).

---

## 1. Protocol Conventions

- **Protocol Version**: Current protocol version is `1`.
- **Protocol Header**: All JSON and streaming responses include:
  ```http
  X-LocalStream-Protocol-Version: 1
  ```
- **Character Encoding**: UTF-8.
- **CORS Support**: Cross-Origin Resource Sharing is enabled for all LAN origins (`Access-Control-Allow-Origin: *`, `Access-Control-Allow-Credentials: true`). Preflight `OPTIONS` requests are handled automatically with HTTP 204.
- **Client Discovery**: The server advertises its HTTP port and host via mDNS service type `_localstream._tcp.local.`.

---

## 2. Authentication & Authorization

LocalStream supports an optional Access PIN configured on the host Android device.

### Auth Extraction Precedence
When an Access PIN is configured, protected endpoints require authorization verified in the following priority order:
1. `X-LocalStream-Token: <token>` HTTP header
2. `ls_token=<token>` cookie in the `Cookie` HTTP header
3. `?token=<token>` query string parameter (specifically supported for external players like VLC that cannot provide custom headers or cookies)

### Unauthenticated Endpoints (Auth Bypass)
The following routes never require an Access PIN:
- `OPTIONS *` (CORS preflight)
- `GET /api/v1/info`
- `GET /api/v1/auth/config`
- `POST /api/v1/auth/verify`
- `POST /api/v1/auth/logout`

---

## 3. Standard Error Envelope

All API errors return a uniform JSON envelope with appropriate HTTP status codes:

```json
{
  "error": {
    "code": "MEDIA_NOT_FOUND",
    "message": "Media item not found: item-xyz"
  }
}
```

### Standard Error Codes

| Status Code | Error Code | Description |
| :--- | :--- | :--- |
| `400 Bad Request` | `BAD_REQUEST` | Malformed request line, oversized body, or invalid syntax |
| `400 Bad Request` | `INVALID_ID` | Item or folder ID contains illegal path characters or invalid format |
| `401 Unauthorized` | `AUTH_REQUIRED` | Server requires an Access PIN but none was provided |
| `401 Unauthorized` | `AUTH_INVALID_PIN` | The supplied PIN is incorrect |
| `404 Not Found` | `NOT_FOUND` | Unknown API endpoint |
| `404 Not Found` | `MEDIA_NOT_FOUND` | Media item does not exist in library |
| `404 Not Found` | `THUMB_UNAVAILABLE` | Media does not contain an extractable thumbnail |
| `410 Gone` | `MEDIA_UNAVAILABLE` | File was indexed but has been deleted from device storage |
| `416 Range Not Satisfiable` | `INVALID_RANGE` | Byte range exceeds file boundaries (`Content-Range: bytes */TOTAL`) |
| `431 Request Header Fields Too Large` | `REQUEST_HEADER_FIELDS_TOO_LARGE` | Client sent more than 100 HTTP headers |
| `500 Internal Server Error` | `INTERNAL_ERROR` | Unhandled runtime exception |
| `501 Not Implemented` | `NOT_IMPLEMENTED` | Feature not supported on device (e.g. live video transcoding) |

---

## 4. Endpoints Reference

### 4.1 System & State

#### `GET /api/v1/info`
Returns general metadata about the server instance.

- **Auth Required**: No
- **Response**: `200 OK`
```json
{
  "serverName": "LocalStream",
  "version": "1.0.1",
  "protocolVersion": 1,
  "port": 8080
}
```

---

#### `GET /api/v1/status`
Returns real-time runtime metrics and server state.

- **Auth Required**: If PIN configured
- **Response**: `200 OK`
```json
{
  "state": "RUNNING",
  "port": 8080,
  "addresses": [
    "192.168.1.105",
    "10.0.0.1"
  ],
  "activeClients": 2,
  "activeStreams": 1,
  "bytesTransferred": 14285712,
  "errorMessage": null
}
```

---

#### `GET /api/v1/clients`
Returns a list of client devices that have connected to this server session.

- **Auth Required**: If PIN configured
- **Response**: `200 OK`
```json
[
  {
    "ip": "192.168.1.140",
    "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)...",
    "clientName": "Chrome on macOS",
    "clientType": "Browser",
    "firstSeen": 1727500000000,
    "lastSeen": 1727500320000,
    "currentItem": "Big Buck Bunny.mp4",
    "bytesTransferred": 52428800
  }
]
```

---

### 4.2 Media Library

#### `GET /api/v1/files`
Lists all media files available in the library. If `parent` query parameter is provided, returns the folder contents view.

- **Query Parameters**:
  - `parent` *(optional, string)*: Virtual folder ID
- **Response**: `200 OK`
```json
[
  {
    "id": "tree:doc-1234",
    "displayName": "Inception.2010.1080p.mkv",
    "mimeType": "video/x-matroska",
    "mediaType": "video",
    "sizeBytes": 2147483648,
    "durationMs": 8880000,
    "subtitles": [
      {
        "id": "sub:doc-5678",
        "label": "English (External)",
        "language": "en",
        "format": "vtt"
      }
    ]
  }
]
```

---

#### `GET /api/v1/files/:id`
Returns detailed metadata for a single media item.

- **URL Parameters**:
  - `id`: Unique identifier of the media item
- **Response**: `200 OK`
```json
{
  "id": "tree:doc-1234",
  "displayName": "Inception.2010.1080p.mkv",
  "mimeType": "video/x-matroska",
  "mediaType": "video",
  "sizeBytes": 2147483648,
  "durationMs": 8880000,
  "subtitles": []
}
```

---

#### `GET /api/v1/folders`
Navigates the virtual folder directory tree.

- **Query Parameters**:
  - `parent` *(optional, string)*: Parent folder ID (omit for root folders)
- **Response**: `200 OK`
```json
[
  {
    "id": "folder:downloads",
    "name": "Movies",
    "itemCount": 12,
    "subfolderCount": 2
  }
]
```

---

### 4.3 Streaming & Thumbnails

#### `GET /api/v1/stream/:id`
Streams media bytes. Supports standard HTTP Range requests for seeking and pause/resume.

- **Headers**:
  - `Range`: `bytes=<start>-<end>` (e.g. `bytes=0-`, `bytes=1048576-2097151`, `bytes=-524288`)
- **Responses**:
  - `206 Partial Content`: When a valid `Range` header is present.
    - `Content-Range: bytes 0-1048575/2147483648`
    - `Content-Length: 1048576`
    - `Content-Type: video/mp4`
    - `Accept-Ranges: bytes`
    - `Content-Disposition: inline; filename="..."`
  - `200 OK`: When streaming the full file from offset 0 without range headers.
  - `416 Range Not Satisfiable`: When requested byte range is invalid or exceeds file size.
    - `Content-Range: bytes */2147483648`

#### `HEAD /api/v1/stream/:id`
Returns identical headers to `GET /api/v1/stream/:id` without transmitting the response body. Used by video players to probe file size and range capabilities.

---

#### `GET /api/v1/thumb/:id`
Returns a JPEG thumbnail extracted from the video file.

- **Response**: `200 OK`
  - `Content-Type: image/jpeg`
  - `Cache-Control: public, max-age=86400`
  - `ETag: "thumb-<id>"`

#### `HEAD /api/v1/thumb/:id`
Probes thumbnail existence.

---

### 4.4 Real-time Events & Transcoding

#### `GET /api/v1/events`
Opens a persistent Server-Sent Events (SSE) connection. Pushes real-time notifications to connected clients.

- **Headers**:
  - `Accept: text/event-stream`
- **Response**: `200 OK` (Stream)
  - `Content-Type: text/event-stream; charset=utf-8`
  - `Cache-Control: no-cache`
- **Events**:
  - `hello`: Dispatched immediately upon connection.
  - `library`: Dispatched when media items or folders are added or deleted.
  - `import`: Progress report on background file imports.
  - `: ping <seq>`: Heartbeat comment sent every 20 seconds to keep connection alive.

---

#### `GET /api/v1/transcode/:id`
Reserved endpoint for live video transcoding status.

- **Response**: `501 Not Implemented`
```json
{
  "id": "tree:doc-1234",
  "transcodingSupported": false,
  "message": "Live on-device transcoding is not supported. Use direct stream with VLC or compatible client."
}
```

---

### 4.5 Authentication

#### `GET /api/v1/auth/config`
Queries the server authentication mode.

- **Response**: `200 OK`
```json
{
  "pinRequired": true,
  "serverName": "LocalStream",
  "protocolVersion": 1
}
```

---

#### `POST /api/v1/auth/verify`
Authenticates with the server PIN and exchanges it for a session token.

- **Request Body**:
```json
{
  "pin": "1234"
}
```
- **Response**: `200 OK`
  - `Set-Cookie: ls_token=<token>; Path=/; Max-Age=2592000; SameSite=Lax`
```json
{
  "token": "7f8a9b...",
  "expiresInMs": 2592000000,
  "protocolVersion": 1
}
```

---

#### `POST /api/v1/auth/logout`
Invalidates the current session token.

- **Response**: `200 OK`
  - `Set-Cookie: ls_token=; Path=/; Max-Age=0; SameSite=Lax`
```json
{
  "ok": true
}
```

---

### 4.6 Web Application Shell

#### `GET /` & `GET /index.html`
Serves the complete single-page browser web application.

- **Response**: `200 OK`
  - `Content-Type: text/html; charset=utf-8`

#### `GET /watch/:id`
Deep-links the web application directly into video playback mode for the given media item ID.
