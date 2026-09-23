class MediaItem {
  final String id;
  final String name;
  final String mimeType;
  final int size;
  final String type; // "video", "audio", "image", "other"
  final String source; // "saf", "shared", "imported"
  final bool available;

  const MediaItem({
    required this.id,
    required this.name,
    required this.mimeType,
    required this.size,
    required this.type,
    required this.source,
    required this.available,
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
    );
  }

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
}
