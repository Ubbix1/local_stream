# Architecture

```text
Phone Flutter UI
      |
 MethodChannel
      v
Kotlin Android layer
  |-- Foreground Service
  |-- Storage Access Framework
  |-- Share Intent handler
  |-- NSD/mDNS
  |-- HTTP Media Server + Range
  |
  +--> MediaSource abstraction
          |-- SAF folder
          |-- Shared content URI
          |-- App-owned copy
          |-- USB/OTG / SD

HTTP clients: Browser | TV app | other LAN clients
```

The TV app is optional. Any LAN browser can access the server by IP/hostname.
`localhost` refers to the device itself; remote clients use the phone's LAN IP or
an mDNS hostname such as `localstream.local` when supported.

Share flow is source-agnostic: Telegram, WhatsApp, browser, file manager, Downloads,
or any other app may provide a URI. Never branch on sender-app name.
