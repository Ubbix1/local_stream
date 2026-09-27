class ConnectedPlayback {
  final String id;
  final String name;
  final int startedAtMs;
  final bool active;
  final int bytes;

  const ConnectedPlayback({
    required this.id,
    required this.name,
    required this.startedAtMs,
    required this.active,
    required this.bytes,
  });

  factory ConnectedPlayback.fromMap(Map<dynamic, dynamic> map) {
    return ConnectedPlayback(
      id: map['id']?.toString() ?? '',
      name: map['name']?.toString() ?? '',
      startedAtMs: (map['startedAtMs'] as num?)?.toInt() ?? 0,
      active: map['active'] as bool? ?? false,
      bytes: (map['bytes'] as num?)?.toInt() ?? 0,
    );
  }
}

class ConnectedClient {
  final String ip;
  final String label;
  final String kind; // vlc | browser | app | other
  final String? browser;
  final String? browserVersion;
  final String? platform;
  final String? device;
  final String? hostname;
  final bool online;
  final int firstSeenMs;
  final int lastSeenMs;
  final int bytes;
  final List<ConnectedPlayback> playing;

  const ConnectedClient({
    required this.ip,
    required this.label,
    required this.kind,
    this.browser,
    this.browserVersion,
    this.platform,
    this.device,
    this.hostname,
    required this.online,
    required this.firstSeenMs,
    required this.lastSeenMs,
    required this.bytes,
    required this.playing,
  });

  factory ConnectedClient.fromMap(Map<dynamic, dynamic> map) {
    return ConnectedClient(
      ip: map['ip']?.toString() ?? '',
      label: map['label']?.toString() ?? '',
      kind: map['kind']?.toString() ?? 'other',
      browser: map['browser'] as String?,
      browserVersion: map['browserVersion'] as String?,
      platform: map['platform'] as String?,
      device: map['device'] as String?,
      hostname: map['hostname'] as String?,
      online: map['online'] as bool? ?? false,
      firstSeenMs: (map['firstSeenMs'] as num?)?.toInt() ?? 0,
      lastSeenMs: (map['lastSeenMs'] as num?)?.toInt() ?? 0,
      bytes: (map['bytes'] as num?)?.toInt() ?? 0,
      playing: (map['playing'] as List<dynamic>?)
              ?.whereType<Map<dynamic, dynamic>>()
              .map(ConnectedPlayback.fromMap)
              .toList() ??
          const [],
    );
  }

  String get subtitle {
    final parts = <String>[
      switch (kind) {
        'vlc' => 'VLC${browserVersion != null ? ' $browserVersion' : ''}',
        'browser' =>
          '${browser ?? 'Browser'}${browserVersion != null ? ' $browserVersion' : ''}',
        _ => browser ?? platform ?? 'Device',
      },
      if (platform != null) platform!,
      if (device != null) device!,
      ip,
    ].where((e) => e.isNotEmpty).toList();
    return parts.join(' · ');
  }
}