# Bootstrap

Flutter was not available in the build environment when this backbone was created,
so generated Gradle/Flutter template files are intentionally not pinned here.

On a Flutter-enabled machine:

1. `flutter create --platforms=android apps/mobile`
2. `flutter create --platforms=android apps/tv`
3. Merge the supplied `lib/` and Android Kotlin files.
4. Add Android dependencies for Media3 and your chosen embedded HTTP server.
5. Implement the contracts in `docs/`.

Build order: share/storage → HTTP Range → browser → mDNS → TV Media3 → resilience tests → optional import/transcode/DLNA/HTTPS/auth.
