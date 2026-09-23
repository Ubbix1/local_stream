enum ServerLifecycleState {
  stopped,
  starting,
  running,
  stopping,
  error;

  static ServerLifecycleState fromString(String? val) {
    return ServerLifecycleState.values.firstWhere(
      (e) => e.name.toLowerCase() == (val ?? '').toLowerCase(),
      orElse: () => ServerLifecycleState.stopped,
    );
  }

  bool get isRunning => this == ServerLifecycleState.running;
  bool get isStarting => this == ServerLifecycleState.starting;
  bool get isStopped => this == ServerLifecycleState.stopped;
  bool get isStopping => this == ServerLifecycleState.stopping;
}

class ServerStatus {
  final ServerLifecycleState state;
  final int port;
  final List<String> addresses;
  final int activeClients;
  final int activeStreams;
  final int bytesTransferred;
  final int uptimeMs;
  final String? errorMessage;

  const ServerStatus({
    this.state = ServerLifecycleState.stopped,
    this.port = 8080,
    this.addresses = const [],
    this.activeClients = 0,
    this.activeStreams = 0,
    this.bytesTransferred = 0,
    this.uptimeMs = 0,
    this.errorMessage,
  });

  factory ServerStatus.fromMap(Map<dynamic, dynamic> map) {
    return ServerStatus(
      state: ServerLifecycleState.fromString(map['state'] as String?),
      port: (map['port'] as num?)?.toInt() ?? 8080,
      addresses: (map['addresses'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      activeClients: (map['activeClients'] as num?)?.toInt() ?? 0,
      activeStreams: (map['activeStreams'] as num?)?.toInt() ?? 0,
      bytesTransferred: (map['bytesTransferred'] as num?)?.toInt() ?? 0,
      uptimeMs: (map['uptimeMs'] as num?)?.toInt() ?? 0,
      errorMessage: map['errorMessage'] as String?,
    );
  }

  String get primaryUrl {
    final ip = addresses.isNotEmpty ? addresses.first : '127.0.0.1';
    return 'http://$ip:$port';
  }
}
