class FolderEntry {
  const FolderEntry({
    required this.id,
    required this.name,
    required this.itemCount,
  });

  final String id;
  final String name;
  final int itemCount;

  factory FolderEntry.fromJson(Map<String, dynamic> json) {
    final rawCount = json['itemCount'];
    return FolderEntry(
      id: '${json['id'] ?? ''}',
      name: '${json['name'] ?? 'Folder'}',
      itemCount: rawCount is num ? rawCount.toInt() : 0,
    );
  }
}

class MediaEntry {
  const MediaEntry({
    required this.id,
    required this.name,
    required this.type,
    required this.mimeType,
    required this.size,
    this.folderId,
    this.folderPath,
    this.thumbUrl,
    this.durationMs,
    this.subtitles = const [],
  });

  final String id;
  final String name;
  final String type;
  final String mimeType;
  final int size;
  final String? folderId;
  final String? folderPath;
  final String? thumbUrl;
  final int? durationMs;
  final List<SubtitleTrack> subtitles;

  bool get isPlayable => type == 'video' || type == 'audio';

  factory MediaEntry.fromJson(Map<String, dynamic> json) {
    final rawSize = json['size'];
    final rawDuration = json['durationMs'];
    final rawSubs = json['subtitles'];
    return MediaEntry(
      id: '${json['id'] ?? ''}',
      name: '${json['name'] ?? 'Untitled'}',
      type: '${json['type'] ?? 'unknown'}',
      mimeType: '${json['mimeType'] ?? 'application/octet-stream'}',
      size: rawSize is num ? rawSize.toInt() : -1,
      folderId: json['folderId'] is String ? json['folderId'] as String : null,
      folderPath: json['folderPath'] is String ? json['folderPath'] as String : null,
      thumbUrl: json['thumbUrl'] is String ? json['thumbUrl'] as String : null,
      durationMs: rawDuration is num ? rawDuration.toInt() : null,
      subtitles: [
        for (final t in (rawSubs as List?) ?? const <dynamic>[])
          if (t is Map<String, dynamic>) SubtitleTrack.fromJson(t),
      ],
    );
  }
}

class SubtitleTrack {
  const SubtitleTrack({required this.id, required this.name, required this.mimeType});

  final String id;
  final String name;
  final String mimeType;

  factory SubtitleTrack.fromJson(Map<String, dynamic> json) => SubtitleTrack(
        id: '${json['id'] ?? ''}',
        name: '${json['name'] ?? 'Subtitle'}',
        mimeType: '${json['mimeType'] ?? 'text/vtt'}',
      );
}

class LibraryView {
  const LibraryView({this.folders = const [], this.items = const []});

  final List<FolderEntry> folders;
  final List<MediaEntry> items;

  static LibraryView fromFolderJson(Map<String, dynamic> json) {
    final folders = json['folders'];
    final items = json['items'];
    return LibraryView(
      folders: [
        for (final f in (folders as List?) ?? const <dynamic>[])
          if (f is Map<String, dynamic>) FolderEntry.fromJson(f),
      ],
      items: [
        for (final i in (items as List?) ?? const <dynamic>[])
          if (i is Map<String, dynamic>) MediaEntry.fromJson(i),
      ],
    );
  }
}