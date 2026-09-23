enum MediaSourceType { saf, sharedUri, appStorage, usb, sdCard }
enum MediaKind { directory, video, audio, image, unknown }
class MediaItem {
  final String id, name, mimeType;
  final int? size;
  final MediaKind kind;
  final MediaSourceType source;
  final bool available;
  const MediaItem({required this.id, required this.name, required this.mimeType, required this.size, required this.kind, required this.source, required this.available});
}
class ServerInfo { final String name, version; final int port; const ServerInfo({required this.name, required this.version, required this.port}); }
