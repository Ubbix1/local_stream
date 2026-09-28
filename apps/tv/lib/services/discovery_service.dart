import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class DiscoveredServer {
  const DiscoveredServer({
    required this.name,
    required this.host,
    required this.port,
  });

  final String name;
  final String host;
  final int port;

  String get baseUrl => 'http://$host:$port';
}

/// Resolves LocalStream servers advertised over mDNS (`_http._tcp.`) by the
/// Android server app. The server publishes no identifying TXT records, so
/// every candidate is still verified over HTTP by the caller
/// (GET /api/v1/info, filtered on `name == "LocalStream"`).
class DiscoveryService {
  DiscoveryService()
      : _events = const EventChannel('localstream.tv/discovery/events');

  static const MethodChannel _method =
      MethodChannel('localstream.tv/discovery');

  static bool get supported => defaultTargetPlatform == TargetPlatform.android;

  final EventChannel _events;

  final StreamController<DiscoveredServer> _controller =
      StreamController<DiscoveredServer>.broadcast();

  StreamSubscription<dynamic>? _subscription;
  bool _started = false;

  /// Servers reported by the platform as they are found.
  Stream<DiscoveredServer> get stream => _controller.stream;

  Future<void> start() async {
    if (_started || !supported) return;
    _started = true;
    await _subscription?.cancel();
    _subscription = _events.receiveBroadcastStream().listen(
      (raw) {
        try {
          final map = Map<String, dynamic>.from(raw as Map);
          final server = DiscoveredServer(
            name: map['name'] as String? ?? 'LocalStream',
            host: map['host'] as String,
            port: (map['port'] as num).toInt(),
          );
          if (!_controller.isClosed) _controller.add(server);
        } catch (_) {
          // Ignore malformed platform events.
        }
      },
      onError: (_) => _started = false,
    );
  }

  Future<void> stop() async {
    if (!_started) return;
    _started = false;
    await _subscription?.cancel();
    _subscription = null;
    try {
      await _method.invokeMethod<void>('stopDiscovery');
    } catch (_) {
      // Channel unavailable (e.g. non-Android preview): nothing to stop.
    }
  }

  void dispose() {
    _started = false;
    _subscription?.cancel();
    _subscription = null;
    _controller.close();
  }
}
