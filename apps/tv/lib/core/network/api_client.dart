import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../errors/app_error.dart';

/// Minimal HTTP JSON client for the LocalStream server API.
///
/// Auth: when a PIN is required the server issues a 30-day token. Requests
/// carry it in the `X-LocalStream-Token` header (the web client uses a cookie
/// instead, which browser media elements can send automatically).
class ApiClient {
  ApiClient(this.baseUrl);

  /// Base URL, e.g. `http://192.168.1.20:8080` (no trailing slash).
  final String baseUrl;

  /// Optional session token, sent as `X-LocalStream-Token`.
  String? token;

  Uri _uri(String path) => Uri.parse('$baseUrl$path');

  Map<String, String> get _headers => {
        'Accept': 'application/json',
        if (token != null) 'X-LocalStream-Token': token!,
      };

  Future<Map<String, dynamic>> getJson(String path) async {
    final res = await http
        .get(_uri(path), headers: _headers)
        .timeout(const Duration(seconds: 15));
    return _decode(res);
  }

  Future<Map<String, dynamic>> postJson(
    String path,
    Map<String, dynamic> body,
  ) async {
    final res = await http
        .post(
          _uri(path),
          headers: {..._headers, 'Content-Type': 'application/json'},
          body: jsonEncode(body),
        )
        .timeout(const Duration(seconds: 15));
    return _decode(res);
  }

  Map<String, dynamic> _decode(http.Response res) {
    if (res.statusCode == 401) {
      throw const AppError(
        ErrorCode.permissionDenied,
        'Access denied — this server requires a PIN.',
      );
    }
    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw AppError(ErrorCode.network, 'Server error (${res.statusCode})');
    }
    try {
      return jsonDecode(res.body) as Map<String, dynamic>;
    } catch (_) {
      throw const AppError(
        ErrorCode.unsupported,
        'Unexpected response from the server.',
      );
    }
  }
}

/// Wraps [AppError] transport failures with a friendly message.
AppError asAppError(Object error) {
  if (error is AppError) return error;
  if (error is TimeoutException) {
    return const AppError(ErrorCode.network, 'Server did not respond in time.');
  }
  return const AppError(
    ErrorCode.network,
    'Could not reach the server. Check the address and that it is powered on.',
  );
}