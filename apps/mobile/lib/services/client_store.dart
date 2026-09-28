import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/connected_client.dart';

/// Persists the set of devices that have connected to the local server so the
/// "Connected Clients" screen remembers them across app restarts. Every poll
/// merges the live snapshots into the stored set; live clients stay marked
/// online while remembered-but-idle clients are shown as away.
class ClientStore extends ChangeNotifier {
  static const String _prefsKey = 'remembered_clients_v1';
  static const int _retentionDays = 30;

  SharedPreferences? _prefs;
  final Map<String, ConnectedClient> _byIp = {};
  Timer? _persistTimer;

  /// Loads the stored device list. Safe to call before [init] has finished;
  /// the store simply starts empty until persisted data arrives.
  Future<void> init() async {
    try {
      _prefs = await SharedPreferences.getInstance();
      _load();
      notifyListeners();
    } catch (_) {}
  }

  /// All remembered devices, live ones first, then most-recently-active.
  List<ConnectedClient> get clients {
    final list = _byIp.values.toList()
      ..sort((a, b) {
        if (a.online != b.online) return a.online ? -1 : 1;
        return b.lastSeenMs.compareTo(a.lastSeenMs);
      });
    return list;
  }

  void _load() {
    final raw = _prefs?.getString(_prefsKey) ?? '';
    if (raw.isEmpty) return;
    try {
      final cutoff = DateTime.now().millisecondsSinceEpoch -
          _retentionDays * 24 * 60 * 60 * 1000;
      final decoded =
          (jsonDecode(raw) as List<dynamic>).whereType<Map<dynamic, dynamic>>();
      for (final map in decoded) {
        final client = ConnectedClient.fromMap(map);
        if (client.ip.isEmpty || client.lastSeenMs < cutoff) continue;
        _byIp[client.ip] = client;
      }
    } catch (_) {
      _byIp.clear();
    }
  }

  /// Merges a fresh live snapshot into the remembered set and flushes the
  /// result to local storage (debounced).
  Future<void> update(Iterable<ConnectedClient> live) async {
    var changed = false;
    final now = DateTime.now().millisecondsSinceEpoch;
    final seen = <String>{};

    for (final liveClient in live) {
      seen.add(liveClient.ip);
      final existing = _byIp[liveClient.ip];
      final merged = _merge(existing, liveClient);
      final unchanged = existing != null &&
          jsonEncode(existing.toMap()) == jsonEncode(merged.toMap());
      if (!unchanged) {
        _byIp[liveClient.ip] = merged;
        changed = true;
      }
    }

    for (final entry in _byIp.values.toList()) {
      if (seen.contains(entry.ip)) continue;
      if (now - entry.lastSeenMs > _retentionDays * 24 * 60 * 60 * 1000) {
        _byIp.remove(entry.ip);
        changed = true;
      } else if (entry.online) {
        _byIp[entry.ip] = _copied(entry, online: false);
        changed = true;
      }
    }

    if (changed) {
      notifyListeners();
      _schedulePersist();
    }
  }

  /// Combines whatever we already knew about a device with the freshest data
  /// so aggregated values (first seen, total bytes) survive app restarts.
  ConnectedClient _merge(ConnectedClient? existing, ConnectedClient live) {
    if (existing == null) return live;
    final firstSeen = existing.firstSeenMs == 0
        ? live.firstSeenMs
        : (live.firstSeenMs == 0
            ? existing.firstSeenMs
            : existing.firstSeenMs < live.firstSeenMs
                ? existing.firstSeenMs
                : live.firstSeenMs);
    final bytes = live.bytes >= existing.bytes ? live.bytes : existing.bytes;
    return _copied(
      ConnectedClient(
        ip: live.ip,
        label: live.label,
        kind: live.kind,
        browser: live.browser,
        browserVersion: live.browserVersion,
        platform: live.platform,
        device: live.device,
        hostname: live.hostname,
        userAgent: live.userAgent,
        online: live.online,
        firstSeenMs: firstSeen,
        lastSeenMs: live.lastSeenMs,
        bytes: bytes,
        playing: live.playing,
      ),
    );
  }

  ConnectedClient _copied(ConnectedClient c, {bool? online}) {
    return ConnectedClient(
      ip: c.ip,
      label: c.label,
      kind: c.kind,
      browser: c.browser,
      browserVersion: c.browserVersion,
      platform: c.platform,
      device: c.device,
      hostname: c.hostname,
      userAgent: c.userAgent,
      online: online ?? c.online,
      firstSeenMs: c.firstSeenMs,
      lastSeenMs: c.lastSeenMs,
      bytes: c.bytes,
      playing: c.playing,
    );
  }

  void _schedulePersist() {
    _persistTimer?.cancel();
    _persistTimer = Timer(const Duration(seconds: 3), _persist);
  }

  /// Immediately writes the current remembered set to storage (normally this
  /// happens on a short debounce). Exposed so callers can flush on shutdown.
  Future<void> flush() => _persist();

  /// Forgets a remembered device and immediately persists the change.
  /// Has no effect (and does not persist) when the device was never stored.
  Future<void> forget(String ip) async {
    if (_byIp.remove(ip) == null) return;
    notifyListeners();
    await _persist();
  }

  Future<void> _persist() async {
    final list = _byIp.values.map((c) => c.toMap()).toList();
    try {
      await _prefs?.setString(_prefsKey, jsonEncode(list));
    } catch (_) {}
  }

  @override
  void dispose() {
    _persistTimer?.cancel();
    super.dispose();
  }
}
