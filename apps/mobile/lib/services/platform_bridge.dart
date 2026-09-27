import 'package:flutter/services.dart';
import '../models/app_version.dart';
import '../models/connected_client.dart';
import '../models/media_item.dart';
import '../models/server_status.dart';

class PlatformBridge {
  static const MethodChannel _methodChannel = MethodChannel('localstream/mobile');
  static const EventChannel _eventChannel = EventChannel('localstream/mobile/events');

  static final PlatformBridge _instance = PlatformBridge._internal();
  factory PlatformBridge() => _instance;
  PlatformBridge._internal();

  Stream<Map<dynamic, dynamic>>? _events;

  Stream<Map<dynamic, dynamic>> get events {
    _events ??= _eventChannel
        .receiveBroadcastStream()
        .map((event) => (event as Map).cast<dynamic, dynamic>());
    return _events!;
  }

  Future<bool> startServer({int port = 8080}) async {
    try {
      final res = await _methodChannel.invokeMethod<bool>('startServer', {'port': port});
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> stopServer() async {
    try {
      final res = await _methodChannel.invokeMethod<bool>('stopServer');
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<ServerStatus> getStatus() async {
    try {
      final map = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>('getStatus');
      if (map != null) {
        return ServerStatus.fromMap(map);
      }
    } catch (_) {}
    return const ServerStatus();
  }

  Future<AppVersion> getAppVersion() async {
    try {
      final map = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>('getAppVersion');
      if (map != null) {
        return AppVersion(
          versionName: (map['versionName'] as String?) ?? '',
          versionCode: (map['versionCode'] as int?) ?? 0,
        );
      }
    } catch (_) {}
    return const AppVersion();
  }

  Future<List<String>> getNetworkAddresses() async {
    try {
      final list = await _methodChannel.invokeMethod<List<dynamic>>('getNetworkAddresses');
      return list?.map((e) => e.toString()).toList() ?? const [];
    } catch (_) {
      return const [];
    }
  }

  Future<List<ConnectedClient>> getClients() async {
    try {
      final list = await _methodChannel.invokeMethod<List<dynamic>>('getClients');
      if (list != null) {
        return list
            .whereType<Map<dynamic, dynamic>>()
            .map(ConnectedClient.fromMap)
            .toList();
      }
    } catch (_) {}
    return const [];
  }

  Future<List<MediaItem>> listFiles() async {
    try {
      final list = await _methodChannel.invokeMethod<List<dynamic>>('listFiles');
      if (list != null) {
        return list
            .whereType<Map<dynamic, dynamic>>()
            .map((m) => MediaItem.fromMap(m))
            .toList();
      }
    } catch (_) {}
    return const [];
  }

  Future<int> addSafFolder(String uri) async {
    try {
      final count = await _methodChannel.invokeMethod<int>('addSafFolder', {'uri': uri});
      return count ?? 0;
    } catch (_) {
      return 0;
    }
  }

  Future<int> pickMediaFiles() async {
    try {
      final count = await _methodChannel.invokeMethod<int>('pickMediaFiles');
      return count ?? 0;
    } catch (_) {
      return 0;
    }
  }

  Future<bool> removeMediaItem(String id) async {
    try {
      final res = await _methodChannel.invokeMethod<bool>('removeMediaItem', {'id': id});
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<MediaItem?> importMediaItem(String uri, {String? targetName}) async {
    try {
      final map = await _methodChannel.invokeMethod<Map<dynamic, dynamic>>('importMediaItem', {
        'uri': uri,
        'targetName': targetName,
      });
      if (map != null) {
        return MediaItem.fromMap(map);
      }
    } catch (_) {}
    return null;
  }

  Future<List<String>> getPendingShares() async {
    try {
      final list = await _methodChannel.invokeMethod<List<dynamic>>('getPendingShares');
      return list?.map((e) => e.toString()).toList() ?? const [];
    } catch (_) {
      return const [];
    }
  }
}
