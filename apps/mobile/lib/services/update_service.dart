import 'dart:async';
import 'dart:convert';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'platform_bridge.dart';

/// Polls the GitHub Releases API for the newest LocalStream build and signals
/// when it is newer than the installed version. The check runs once at startup
/// and again when the user asks for it — it must never throw into the UI.
class UpdateService extends ChangeNotifier {
  UpdateService({PlatformBridge? bridge}) : _bridge = bridge ?? PlatformBridge();

  final PlatformBridge _bridge;

  String installedVersion = '';
  int installedCode = 0;
  String latestVersion = '';
  String latestReleaseUrl = '';
  String latestAssetUrl = '';
  bool checking = false;
  String? error;

  bool _updateAvailable = false;
  bool _dismissed = false;

  bool get updateAvailable => _updateAvailable && !_dismissed;

  Future<void> init() async {
    final version = await _bridge.getAppVersion();
    installedVersion = version.versionName;
    installedCode = version.versionCode;
    notifyListeners();

    // Never hit the network under `flutter test`.
    if (Platform.environment.containsKey('FLUTTER_TEST')) return;
    await check();
  }

  Future<void> check({bool force = false}) async {
    if (checking) return;
    if (force) _dismissed = false;
    checking = true;
    error = null;
    notifyListeners();
    try {
      final res = await http
          .get(
            Uri.parse('https://api.github.com/repos/Ubbix1/local_stream/releases/latest'),
            headers: const {
              'Accept': 'application/vnd.github+json',
              'User-Agent': 'LocalStream',
            },
          )
          .timeout(const Duration(seconds: 10));
      if (res.statusCode != 200) {
        error = 'Update check failed (HTTP ${res.statusCode})';
        return;
      }
      final body = jsonDecode(res.body);
      if (body is! Map<String, dynamic>) {
        error = 'Update check failed';
        return;
      }
      latestVersion = (body['tag_name'] as String?)?.trim() ?? '';
      latestReleaseUrl = (body['html_url'] as String?) ?? '';
      latestAssetUrl = _pickAsset(body['assets']);
      _updateAvailable = isNewer(latestVersion, installedVersion);
    } catch (_) {
      error = 'Update check failed';
    } finally {
      checking = false;
      notifyListeners();
    }
  }

  void dismiss() {
    _dismissed = true;
    notifyListeners();
  }

  void refresh() => check(force: true);

  String _pickAsset(dynamic assets) {
    if (assets is! List) return '';
    for (final asset in assets) {
      if (asset is Map && asset['name'] is String) {
        final name = asset['name'] as String;
        if (name.contains('localstream-mobile')) {
          return asset['browser_download_url']?.toString() ?? '';
        }
      }
    }
    return '';
  }

  static bool isNewer(String tagName, String installed) {
    final latest = versionSegments(tagName);
    final current = versionSegments(installed);
    for (var i = 0; i < latest.length && i < current.length; i++) {
      if (latest[i] != current[i]) return latest[i] > current[i];
    }
    return false;
  }

  /// "v1.0.1-beta" / "1.0" / "1" all normalize to a three-part [major, minor,
  /// patch] list so mismatched formats compare sanely.
  static List<int> versionSegments(String value) {
    final digits = RegExp(r'\d+')
        .allMatches(value)
        .map((m) => int.tryParse(m.group(0) ?? '') ?? 0)
        .toList();
    while (digits.length < 3) {
      digits.add(0);
    }
    return digits.take(3).toList();
  }
}