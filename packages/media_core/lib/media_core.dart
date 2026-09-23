abstract interface class MediaSource {
  String get id; String get name; String? get mimeType;
  Future<int?> length();
  Future<List<int>> readRange(int start, int end);
  Future<void> close();
}
abstract interface class MediaRepository {
  Future<void> add(MediaSource source);
  Future<void> remove(String id);
  Future<List<Object>> list({String? parentId});
  Future<MediaSource?> open(String id);
}
