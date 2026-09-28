import 'package:flutter_test/flutter_test.dart';
import 'package:localstream_mobile/models/connected_client.dart';
import 'package:localstream_mobile/services/client_store.dart';
import 'package:shared_preferences/shared_preferences.dart';

ConnectedClient _client({
  required String ip,
  String label = 'Client',
  String kind = 'browser',
  bool online = true,
  int? firstSeenMs,
  int? lastSeenMs,
  int bytes = 100,
  String? userAgent,
}) {
  final base = DateTime.now().millisecondsSinceEpoch;
  return ConnectedClient(
    ip: ip,
    label: label,
    kind: kind,
    browser: 'Chrome',
    browserVersion: '120',
    platform: 'Windows',
    device: null,
    hostname: 'pc.local',
    userAgent: userAgent,
    online: online,
    firstSeenMs: firstSeenMs ?? base,
    lastSeenMs: lastSeenMs ?? base,
    bytes: bytes,
    playing: const [],
  );
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('ClientStore', () {
    test('starts empty', () async {
      final store = ClientStore();
      addTearDown(store.dispose);
      await store.init();
      expect(store.clients, isEmpty);
    });

    test('remembers live clients and marks them online', () async {
      final store = ClientStore();
      addTearDown(store.dispose);
      await store.init();

      await store.update([_client(ip: '192.168.1.10')]);

      expect(store.clients.length, 1);
      expect(store.clients.single.ip, '192.168.1.10');
      expect(store.clients.single.online, isTrue);
    });

    test('marks remembered clients as away when not in the live set', () async {
      final store = ClientStore();
      addTearDown(store.dispose);
      await store.init();

      await store.update([_client(ip: '192.168.1.10', online: true)]);
      await store.update(const []);

      expect(store.clients.length, 1);
      expect(store.clients.single.online, isFalse);
    });

    test('retains smallest first seen and largest byte count across merges',
        () async {
      final store = ClientStore();
      addTearDown(store.dispose);
      await store.init();

      await store.update([
        _client(
            ip: '192.168.1.10',
            firstSeenMs: 5000,
            lastSeenMs: 2000,
            bytes: 900),
      ]);
      await store.update([
        _client(
            ip: '192.168.1.10',
            firstSeenMs: 9000,
            lastSeenMs: 9500,
            bytes: 300),
      ]);

      final kept = store.clients.single;
      expect(kept.firstSeenMs, 5000);
      expect(kept.bytes, 900);
      expect(kept.lastSeenMs, 9500);
    });

    test('persists clients across store instances', () async {
      SharedPreferences.setMockInitialValues({});
      final first = ClientStore();
      await first.init();
      await first.update([_client(ip: '10.0.0.7', label: 'TV')]);
      // Debounced write is on a 3s timer; force it so the data is on disk.
      await first.flush();
      first.dispose();

      final second = ClientStore();
      addTearDown(second.dispose);
      await second.init();

      expect(second.clients.length, 1);
      expect(second.clients.single.ip, '10.0.0.7');
      expect(second.clients.single.label, 'TV');
    });
  });

  group('ConnectedClient serialization', () {
    test('round-trips through toMap/fromMap including userAgent', () {
      final original = _client(
        ip: '192.168.1.5',
        kind: 'vlc',
        label: 'LivingRoom',
        userAgent: 'VLC/3.0.20 LibVLC/3.0.20',
      );

      final back = ConnectedClient.fromMap(original.toMap());

      expect(back.ip, original.ip);
      expect(back.label, original.label);
      expect(back.kind, original.kind);
      expect(back.userAgent, 'VLC/3.0.20 LibVLC/3.0.20');
      expect(back.online, original.online);
      expect(back.firstSeenMs, original.firstSeenMs);
      expect(back.bytes, original.bytes);
    });

    test('parses playback sessions', () {
      final client = ConnectedClient.fromMap({
        'ip': '10.0.0.2',
        'label': 'Phone',
        'kind': 'browser',
        'online': true,
        'firstSeenMs': 1,
        'lastSeenMs': 2,
        'bytes': 3,
        'playing': [
          {
            'id': 'a',
            'name': 'Clip.mp4',
            'startedAtMs': 10,
            'active': true,
            'bytes': 512
          },
        ],
      });

      expect(client.playing.length, 1);
      expect(client.playing.single.name, 'Clip.mp4');
      expect(client.playing.single.active, isTrue);
    });
  });
}
