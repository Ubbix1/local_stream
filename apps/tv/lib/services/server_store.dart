import 'package:shared_preferences/shared_preferences.dart';

/// Persists the active server address and access token across launches.
class ServerStore {
  static const _keyAddress = 'server_address';
  static const _keyToken = 'server_token';

  Future<String?> address() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyAddress);
  }

  Future<void> saveAddress(String address) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyAddress, address);
  }

  Future<String?> token() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyToken);
  }

  Future<void> saveToken(String? token) async {
    final prefs = await SharedPreferences.getInstance();
    if (token == null || token.isEmpty) {
      await prefs.remove(_keyToken);
    } else {
      await prefs.setString(_keyToken, token);
    }
  }
}