# LocalStream API v1

GET  /api/v1/info
GET  /api/v1/status
GET  /api/v1/files?parent=<id>
GET  /api/v1/files/:id
GET  /api/v1/stream/:id     # HTTP Range required
POST /api/v1/play/:id      # optional future control
GET  /                   # browser client
GET  /watch/:id           # browser player

Stable error shape:
{"error":{"code":"MEDIA_UNAVAILABLE","message":"File is no longer available."}}
Never expose stack traces to clients.
