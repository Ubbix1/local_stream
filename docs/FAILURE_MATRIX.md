# Failure Isolation Matrix

| Failure | Scope | Expected behavior |
|---|---|---|
| Invalid share URI | Share operation | Reject safely; server stays alive |
| Revoked URI | One item | Mark unavailable; other items continue |
| USB/SD removed | Active source | Stop that read/playback; clean resources |
| Invalid Range | One request | Return HTTP 416/appropriate error |
| Client disconnect | One request | Close stream; no global failure |
| Broken video | One player | Release player; return to library |
| mDNS failure | Discovery | Fall back to IP/manual connection |
| Network change | Connections | Rebind/reconnect safely |
| Storage full | Import | Reject operation; preserve existing library |
| Unexpected exception | Current operation | Catch/log/recover where possible |
