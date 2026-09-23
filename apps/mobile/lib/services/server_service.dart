import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/server_status.dart';
import 'platform_bridge.dart';

class ServerService extends ChangeNotifier {
  final PlatformBridge _bridge = PlatformBridge();
  StreamSubscription? _eventSub;
  Timer? _uptimeTimer;

  ServerStatus _status = const ServerStatus();
  ServerStatus get status => _status;

  int _configuredPort = 8080;
  int get configuredPort => _configuredPort;

  ServerService() {
    _init();
  }

  void _init() {
    _eventSub = _bridge.events.listen((event) {
      final type = event['type'] as String?;
      if (type == 'status_changed') {
        final data = event['data'];
        if (data is Map) {
          _status = ServerStatus.fromMap(data);
          notifyListeners();
        }
      } else if (type == 'network_changed') {
        final addrs = (event['addresses'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toList();
        if (addrs != null) {
          _status = ServerStatus(
            state: _status.state,
            port: _status.port,
            addresses: addrs,
            activeClients: _status.activeClients,
            activeStreams: _status.activeStreams,
            bytesTransferred: _status.bytesTransferred,
            uptimeMs: _status.uptimeMs,
            errorMessage: _status.errorMessage,
          );
          notifyListeners();
        }
      }
    });

    // Keep the UI in sync even if a native status_changed event is never
    // delivered (e.g. when the service starts after the Flutter engine
    // subscribed to events). Poll while the server is not fully stopped.
    _uptimeTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!_status.state.isStopped) {
        refreshStatus();
      }
    });

    refreshStatus();
  }

  void setConfiguredPort(int port) {
    if (port >= 1024 && port <= 65535) {
      _configuredPort = port;
      notifyListeners();
    }
  }

  Future<void> refreshStatus() async {
    _status = await _bridge.getStatus();
    notifyListeners();
  }

  Future<bool> startServer() async {
    final success = await _bridge.startServer(port: _configuredPort);
    if (success) {
      await refreshStatus();
    }
    return success;
  }

  Future<bool> stopServer() async {
    final success = await _bridge.stopServer();
    if (success) {
      await refreshStatus();
    }
    return success;
  }

  String get formattedBytesTransferred {
    final bytes = _status.bytesTransferred;
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String get formattedUptime {
    final ms = _status.uptimeMs;
    if (ms <= 0) return '0s';
    final seconds = (ms / 1000).floor();
    final minutes = (seconds / 60).floor();
    final hours = (minutes / 60).floor();
    if (hours > 0) {
      return '${hours}h ${minutes % 60}m';
    }
    if (minutes > 0) {
      return '${minutes}m ${seconds % 60}s';
    }
    return '${seconds}s';
  }

  @override
  void dispose() {
    _eventSub?.cancel();
    _uptimeTimer?.cancel();
    super.dispose();
  }
}
