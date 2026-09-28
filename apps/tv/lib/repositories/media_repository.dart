import 'package:localstream_protocol/protocol.dart';

import '../core/errors/app_error.dart';
import '../core/network/api_client.dart';

/// Talks to the LocalStream HTTP API:
///   GET  /api/v1/auth/config        -> { pinRequired, ... }
///   POST /api/v1/auth/verify        -> { token, ... }
///   GET  /api/v1/folders            -> { folders: [...] }
///   GET  /api/v1/files              -> { items: [...] }          (flat)
///   GET  /api/v1/files?parent=<id>  -> { folders: [...], items: [...] }
///   GET  /api/v1/files/<id>         -> { id, name, streamUrl, ... }
class MediaRepository {
  MediaRepository(this.api);

  final ApiClient api;

  bool get hasToken => api.token != null;

  Future<bool> pinRequired() async {
    try {
      final data = await api.getJson('/api/v1/auth/config');
      return data['pinRequired'] == true;
    } on AppError {
      rethrow;
    } catch (_) {
      throw const AppError(ErrorCode.network, 'Could not reach the server.');
    }
  }

  Future<void> verifyPin(String pin) async {
    final data = await api.postJson('/api/v1/auth/verify', {'pin': pin});
    final token = data['token'];
    if (token is! String || token.isEmpty) {
      throw const AppError(
        ErrorCode.permissionDenied,
        'Server did not issue a session token.',
      );
    }
    api.token = token;
  }

  Future<LibraryView> root() async {
    try {
      final folderJson = await api.getJson('/api/v1/folders');
      final fileJson = await api.getJson('/api/v1/files');
      final flat = LibraryView.fromMap(
          {'folders': const [], 'items': fileJson['items']});
      return LibraryView(
        folders: LibraryView.fromMap(folderJson).folders,
        items: flat.items,
      );
    } catch (_) {
      rethrow;
    }
  }

  Future<LibraryView> openFolder(String folderId) async {
    final data = await api.getJson(
      '/api/v1/files?parent=${Uri.encodeQueryComponent(folderId)}',
    );
    return LibraryView.fromMap(data);
  }

  Future<MediaItem> detail(String id) async {
    final data = await api.getJson('/api/v1/files/${Uri.encodeComponent(id)}');
    return MediaItem.fromMap(data);
  }

  String streamUrl(String id) => '${api.baseUrl}/api/v1/stream/$id';

  String thumbUrl(String id) => '${api.baseUrl}/api/v1/thumb/$id';
}
