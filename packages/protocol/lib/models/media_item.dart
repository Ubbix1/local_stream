enum MediaKind {
  video,
  audio,
  image,
  directory,
  unknown;

  static MediaKind fromString(String? val) {
    return switch (val?.toLowerCase()) {
      'video' => MediaKind.video,
      'audio' => MediaKind.audio,
      'image' => MediaKind.image,
      'directory' || 'folder' => MediaKind.directory,
      _ => MediaKind.unknown,
    };
  }
}

enum MediaSourceType {
  saf,
  sharedUri,
  appStorage,
  imported,
  usb,
  sdCard,
  unknown;

  static MediaSourceType fromString(String? val) {
    return switch (val?.toLowerCase()) {
      'saf' => MediaSourceType.saf,
      'shared' || 'shareduri' => MediaSourceType.sharedUri,
      'imported' || 'appstorage' => MediaSourceType.imported,
      'usb' => MediaSourceType.usb,
      'sdcard' => MediaSourceType.sdCard,
      _ => MediaSourceType.unknown,
    };
  }
}

class MediaItem {
  final String id;
  final String name;
  final String mimeType;
  final int size;
  final String type; // "video", "audio", "image", "other"
  final String source; // "saf", "shared", "imported"
  final bool available;
  final int? durationMs;
  final String? folderId;

  /// Server-relative thumbnail path, e.g. "/api/v1/thumb/<id>". Emitted by
  /// both /api/v1/files and /api/v1/files/:id.
  final String? thumbUrl;

  const MediaItem({
    required this.id,
    required this.name,
    required this.mimeType,
    required this.size,
    required this.type,
    required this.source,
    required this.available,
    this.durationMs,
    this.folderId,
    this.thumbUrl,
  });

  factory MediaItem.fromMap(Map<dynamic, dynamic> map) {
    return MediaItem(
      id: (map['id'] as String?) ?? '',
      name: (map['name'] as String?) ?? 'Unknown media',
      mimeType: (map['mimeType'] as String?) ?? 'application/octet-stream',
      size: (map['size'] as num?)?.toInt() ?? -1,
      type: (map['type'] as String?) ?? 'other',
      source: (map['source'] as String?) ?? 'saf',
      available: (map['available'] as bool?) ?? true,
      durationMs: (map['durationMs'] as num?)?.toInt(),
      folderId: map['folderId'] as String?,
      thumbUrl: map['thumbUrl'] as String?,
    );
  }

  Map<String, dynamic> toMap() => {
        'id': id,
        'name': name,
        'mimeType': mimeType,
        'size': size,
        'type': type,
        'source': source,
        'available': available,
        if (durationMs != null) 'durationMs': durationMs,
        if (folderId != null) 'folderId': folderId,
        if (thumbUrl != null) 'thumbUrl': thumbUrl,
      };

  String get formattedSize {
    if (size <= 0) return 'Unknown size';
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  bool get isVideo => type == 'video';
  bool get isAudio => type == 'audio';
  bool get isImage => type == 'image';
  MediaKind get kind => MediaKind.fromString(type);
  MediaSourceType get sourceType => MediaSourceType.fromString(source);
}
