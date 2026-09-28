import 'dart:async';
import 'dart:convert';
import 'dart:io' show Platform;

import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

/// Polls the GitHub Releases API for the newest LocalStream build and reports
/// whether a newer TV APK is available. The TV app has no provider setup, so
/// state changes are pushed through [onChanged] and the owner calls setState.
class UpdateService {
  UpdateService();

  static const MethodChannel _appChannel = MethodChannel('localstream.tv/app');

  String installedVersion = '';
  String latestVersion = '';
  String latestReleaseUrl = '';
  String latestAssetUrl = '';
  bool updateAvailable = false;
  bool checking = false;
  bool dismissed = false;
  String? error;

  VoidCallback? onChanged;

  Future<void> init() async {
    final version = await _readInstalledVersion();
    installedVersion = version;
    if (Platform.environment.containsKey('FLUTTER_TEST')) return;
    await check();
  }

  Future<void> check() async {
    if (checking) return;
    checking = true;
    error = null;
    _notify();
    try {
      final res = await http.get(
        Uri.parse(
            'https://api.github.com/repos/Ubbix1/local_stream/releases/latest'),
        headers: const {
          'Accept': 'application/vnd.github+json',
          'User-Agent': 'LocalStream TV',
        },
      ).timeout(const Duration(seconds: 10));
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
      updateAvailable = isNewer(latestVersion, installedVersion);
    } catch (_) {
      error = 'Update check failed';
    } finally {
      checking = false;
      _notify();
    }
  }

  void dismiss() {
    dismissed = true;
    _notify();
  }

  Future<String> _readInstalledVersion() async {
    try {
      final map = await _appChannel
          .invokeMethod<Map<dynamic, dynamic>>('getAppVersion');
      return (map?['versionName'] as String?)?.trim() ?? '';
    } catch (_) {
      return '';
    }
  }

  String _pickAsset(dynamic assets) {
    if (assets is! List) return '';
    for (final asset in assets) {
      if (asset is Map && asset['name'] is String) {
        final name = asset['name'] as String;
        if (name.contains('localstream-tv')) {
          return asset['browser_download_url']?.toString() ?? '';
        }
      }
    }
    return '';
  }

  void _notify() => onChanged?.call();

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
