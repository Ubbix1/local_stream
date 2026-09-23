# LocalStream Streaming Workflow

This document describes how to verify playback from the phone media source to the browser display.

## Request path

```text
Media file or content URI
        |
        v
MediaSource.readRange(start, end)
        |
        v
HTTP server on the phone
        |
        v
Wi-Fi or hotspot
        |
        v
Browser HTTP Range requests
        |
        v
Browser buffer and decoder
        |
        v
Displayed video frames
```

Each stream request is independent. The server does not maintain a movie session or a seek cursor. A seek or retry creates another request with its own byte range.

## Verification order

1. Verify HTTP range behavior.
2. Verify network throughput against the movie bitrate.
3. Verify that the playback buffer stays ahead of the current position.
4. Verify actual displayed FPS.
5. Verify dropped frames and rendered frames.

## 1. Verify Range

Find a media ID from `GET /api/v1/files`, then inspect the headers:

```powershell
curl.exe -i -H "Range: bytes=1000000-" http://PHONE_IP:PORT/api/v1/stream/MEDIA_ID
curl.exe -i -H "Range: bytes=5000000-5010000" http://PHONE_IP:PORT/api/v1/stream/MEDIA_ID
curl.exe -i -H "Range: bytes=999999999999-" http://PHONE_IP:PORT/api/v1/stream/MEDIA_ID
```

Expected results:

- Valid range: `206 Partial Content`.
- `Accept-Ranges: bytes` is present.
- `Content-Range` reports the actual inclusive start, end, and total size.
- `Content-Length` equals `end - start + 1`.
- Unsatisfiable range: `416 Range Not Satisfiable` and `Content-Range: bytes */TOTAL_SIZE`.

The server clamps an end beyond EOF to the final byte. It supports bounded, open-ended, and suffix ranges. Multipart ranges such as `bytes=0-100,200-300` are not implemented; normal browser seeking uses separate requests and is supported.

## 2. Verify throughput

Open `/watch/MEDIA_ID` in a browser. The diagnostics panel reports:

- Current throughput: recent bytes received per second.
- Average throughput: bytes received over the page measurement period.
- Movie bitrate: file size divided by media duration.
- Buffer: seconds of media ahead of the playhead.
- Status: `GOOD`, `BUFFERING RISK`, or `INSUFFICIENT BANDWIDTH`.

Interpret the values together. A throughput rate below the movie bitrate with a nearly empty buffer indicates a network bottleneck. A healthy buffer can temporarily hide a low current rate.

## 3. Verify buffering

Use the browser diagnostics while playing and while seeking:

```text
Throughput >= movie bitrate, buffer increasing  -> network is healthy
Throughput < movie bitrate, buffer near zero    -> network bottleneck
Buffer large, playback still stutters            -> inspect decoder/frame metrics
```

Seeking to a distant position should cause a new Range request and the buffer should refill from that position.

## 4. Verify decoder and display performance

The player uses `requestVideoFrameCallback` to count frames actually presented by the browser. It also reads `getVideoPlaybackQuality()` when available.

```text
FPS near source FPS, dropped frames near zero -> decoder/display is healthy
FPS low, dropped frames rising, buffer healthy -> decoder or device bottleneck
FPS normal, buffer near zero                  -> network bottleneck is more likely
```

FPS is measured only while actively playing. It resets during pause, buffering, and playback end.

## 5. Verify interruption and retry behavior

During playback, temporarily interrupt or change the hotspot connection, then restore it. The expected behavior is:

1. The current socket may close and the current range may be incomplete.
2. The browser reports buffering or a network error.
3. The browser retries with another independent Range request.
4. The server opens the requested offset and continues without relying on the failed request.

A failed request must not leave a shared stream cursor or session state behind. The server closes each source stream and socket in the request handler's cleanup path.

## Diagnostic signatures

Healthy playback:

```text
Range: 206
Throughput: 35 Mbps
Movie bitrate: 12 Mbps
Buffer: 20 sec
FPS: 24
Dropped: 0
```

Likely network bottleneck:

```text
Range: 206
Throughput: 4 Mbps
Movie bitrate: 12 Mbps
Buffer: 0.1 sec
FPS: 24
Dropped: 0
```

Likely decoder or device bottleneck:

```text
Range: 206
Throughput: 35 Mbps
Movie bitrate: 12 Mbps
Buffer: 18 sec
FPS: 11
Dropped: 3400
```
