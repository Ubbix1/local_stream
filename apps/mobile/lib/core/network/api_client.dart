abstract interface class ApiClient {
  Future<Map<String, dynamic>> getJson(String path);
  Future<Map<String, dynamic>> postJson(String path, Map<String, dynamic> body);
}
