class ServerInfo {
  final String name;
  final String version;
  final int port;
  final int protocolVersion;

  const ServerInfo({
    required this.name,
    required this.version,
    required this.port,
    this.protocolVersion = 1,
  });

  factory ServerInfo.fromMap(Map<dynamic, dynamic> map) {
    return ServerInfo(
      name: (map['serverName'] ?? map['name'] ?? 'LocalStream').toString(),
      version: (map['version'] ?? '1.0.0').toString(),
      port: (map['port'] as num?)?.toInt() ?? 8080,
      protocolVersion: (map['protocolVersion'] as num?)?.toInt() ?? 1,
    );
  }

  // ApiResponseBuilder emits `name`, `version`, `serverVersion`, `port` and
  // `protocolVersion`. `fromMap` accepts `serverName` as a legacy alias.
  Map<String, dynamic> toMap() => {
        'name': name,
        'version': version,
        'serverVersion': version,
        'port': port,
        'protocolVersion': protocolVersion,
      };
}
