import 'media_item.dart';

class FolderEntry {
  final String id;
  final String name;
  final int itemCount;
  final int subfolderCount;

  const FolderEntry({
    required this.id,
    required this.name,
    required this.itemCount,
    this.subfolderCount = 0,
  });

  factory FolderEntry.fromMap(Map<dynamic, dynamic> map) {
    return FolderEntry(
      id: (map['id'] ?? '').toString(),
      name: (map['name'] ?? 'Folder').toString(),
      itemCount: (map['itemCount'] as num?)?.toInt() ?? 0,
      subfolderCount: (map['subfolderCount'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toMap() => {
        'id': id,
        'name': name,
        'itemCount': itemCount,
        'subfolderCount': subfolderCount,
      };
}

class SubtitleTrack {
  final String id;
  final String name;
  final String mimeType;
  final String language;

  const SubtitleTrack({
    required this.id,
    required this.name,
    required this.mimeType,
    this.language = 'en',
  });

  factory SubtitleTrack.fromMap(Map<dynamic, dynamic> map) {
    return SubtitleTrack(
      id: (map['id'] ?? '').toString(),
      name: (map['name'] ?? map['label'] ?? 'Subtitle').toString(),
      mimeType: (map['mimeType'] ?? 'text/vtt').toString(),
      language: (map['language'] ?? 'en').toString(),
    );
  }

  Map<String, dynamic> toMap() => {
        'id': id,
        'name': name,
        'mimeType': mimeType,
        'language': language,
      };
}

class LibraryView {
  final List<FolderEntry> folders;
  final List<MediaItem> items;

  const LibraryView({
    this.folders = const [],
    this.items = const [],
  });

  factory LibraryView.fromMap(Map<dynamic, dynamic> map) {
    return LibraryView(
      folders: [
        for (final f in (map['folders'] as List?) ?? const <dynamic>[])
          if (f is Map) FolderEntry.fromMap(f),
      ],
      items: [
        for (final i in (map['items'] as List?) ?? const <dynamic>[])
          if (i is Map) MediaItem.fromMap(i),
      ],
    );
  }

  Map<String, dynamic> toMap() => {
        'folders': folders.map((f) => f.toMap()).toList(),
        'items': items.map((i) => i.toMap()).toList(),
      };
}
