# LocalStream Development Guide

This guide describes how to set up the development environment, build all applications, run tests, and debug LocalStream across Flutter, Android native, and Node.js components.

---

## 1. Prerequisites & Environment Setup

Ensure your workstation has the following installed:

- **Flutter SDK**: `3.3.0` or higher (Channel `stable`)
- **Dart SDK**: `^3.3.0`
- **Java Development Kit (JDK)**: JDK 17 (recommended for Gradle 8+)
- **Android SDK & Build Tools**: API 34+ (Android 14) platform tools
- **Node.js**: Version 18.x or 20.x (for the feedback server and tooling)
- **PowerShell** (Windows) or **Bash** (macOS/Linux)
- **cURL**: For validating HTTP endpoints and range requests

---

## 2. Workspace Organization

LocalStream uses a multi-package workspace structure:

| Directory | Type | Description |
| :--- | :--- | :--- |
| `apps/mobile/` | Flutter App | Primary mobile application containing UI and native Kotlin HTTP server |
| `apps/tv/` | Flutter App | Android TV client optimized for 10-foot D-Pad navigation |
| `packages/protocol/` | Dart Package | Shared protocols, data transfer models, and type definitions |
| `packages/media_core/`| Dart Package | Core domain contracts for media repositories and media sources |
| `server/web/` | Node.js Server| Device telemetry and diagnostic feedback receiver service |
| `docs/` | Documentation | Architecture, API, Streaming, and Development specifications |

---

## 3. Building the Applications

### 3.1 Mobile Application (`apps/mobile`)

#### Run Debug on Connected Device or Emulator
```bash
cd apps/mobile
flutter pub get
flutter run
```

#### Build Release APK
```bash
cd apps/mobile
flutter build apk --release
```
*The resulting binary is generated at `apps/mobile/build/app/outputs/flutter-apk/app-release.apk`.*

### 3.2 Android TV Application (`apps/tv`)

#### Connect to Android TV via ADB over Wi-Fi
```bash
adb connect <TV_IP_ADDRESS>:5555
```

#### Run TV App
```bash
cd apps/tv
flutter pub get
flutter run -d <TV_DEVICE_ID>
```

#### Build TV Release APK
```bash
cd apps/tv
flutter build apk --release
```

---

## 4. Running Tests & Quality Verification

LocalStream maintains a strict verification pipeline. Every change must pass linting and unit tests across all packages.

### 4.1 Run Mobile Tests & Analysis
```bash
cd apps/mobile
flutter analyze
flutter test
```

### 4.2 Run TV Tests & Analysis
```bash
cd apps/tv
flutter analyze
flutter test
```

### 4.3 Run Feedback Microservice Tests
```bash
cd server/web
npm test
```

---

## 5. Running the Local Media Server

### 5.1 On a Physical Android Device
1. Launch the **LocalStream** app on your phone.
2. Grant Storage / File permissions when prompted.
3. Tap **Select Folders** or **Add Media** to index videos or audio from internal storage, SD card, or USB OTG.
4. Tap **Start Server**. The app transitions to `RUNNING` and creates a persistent foreground notification displaying the server URL (e.g. `http://192.168.1.105:8080`).
5. Open any web browser on your computer connected to the same Wi-Fi network and navigate to the displayed URL.

### 5.2 On Android Emulator via Reverse Port Forwarding
When running the server inside an Android emulator, expose the server port to your development host machine:
```bash
# Forward host port 8080 to emulator port 8080
adb reverse tcp:8080 tcp:8080

# Now access the server from your computer browser:
http://localhost:8080
```

### 5.3 Running the Optional Feedback / Telemetry Server
To receive diagnostic reports from devices running LocalStream:
```bash
cd server/web
npm start
```
The feedback server listens on `http://localhost:3000` (or the configured `PORT` environment variable).

---

## 6. Debugging & Inspection

### 6.1 Logcat Filtering
Filter Android native logs for LocalStream components:
```bash
adb logcat -s LocalStream:V LocalStreamService:V HttpMediaServer:V HttpRequestHandler:V StorageManager:V
```

### 6.2 Validating Endpoints with cURL
Inspect headers and response payloads without opening a browser:

```powershell
# Check server information
curl.exe http://192.168.1.105:8080/api/v1/info

# Check server runtime status and client count
curl.exe http://192.168.1.105:8080/api/v1/status

# List all indexed files
curl.exe http://192.168.1.105:8080/api/v1/files

# Test HTTP Range seeking (1 MB chunk)
curl.exe -i -H "Range: bytes=1048576-2097151" http://192.168.1.105:8080/api/v1/stream/<MEDIA_ID>
```

### 6.3 Browser Diagnostics
1. Open Chrome/Edge Developer Tools (`F12`) on `http://<PHONE_IP>:8080`.
2. Inspect the **Network** tab to confirm that video playback creates ongoing `206 Partial Content` requests rather than downloading the entire file.
3. Observe the in-player HUD on `/watch/:id` to check live throughput against the movie bitrate.

---

## 7. Development Guidelines & Architectural Rules

1. **Memory Safety**: Never read whole media files into byte arrays or memory buffers. Always use chunked streams (`BoundedInputStream` and `BufferedOutputStream` with 64 KB buffers).
2. **Failure Isolation**: A corrupted media file, broken storage permission, or aborted client connection must never crash the server or affect other concurrent streams.
3. **No UI Coupling**: The Kotlin native server must remain completely operational even if the Flutter UI process terminates. All UI interactions must route through the defined platform channels or HTTP APIs.
4. **Preserve Compatibility**: Keep the `/api/v1/*` HTTP contracts backwards-compatible so that third-party clients and TV apps do not break during server refactoring.
