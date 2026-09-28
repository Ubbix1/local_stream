import 'package:localstream_protocol/protocol.dart';
import 'package:test/test.dart';

void main() {
  group('ServerStatus Model', () {
    test('parses from valid map correctly', () {
      final map = {
        'state': 'running',
        'port': 8080,
        'addresses': ['192.168.1.50', '10.0.0.5'],
        'activeClients': 3,
        'activeStreams': 2,
        'bytesTransferred': 1048576,
        'uptimeMs': 12000,
        'errorMessage': null,
      };

      final status = ServerStatus.fromMap(map);

      expect(status.state, ServerLifecycleState.running);
      expect(status.port, 8080);
      expect(status.addresses.length, 2);
      expect(status.addresses.first, '192.168.1.50');
      expect(status.activeClients, 3);
      expect(status.activeStreams, 2);
      expect(status.bytesTransferred, 1048576);
      expect(status.primaryUrl, 'http://192.168.1.50:8080');
    });

    test('handles fallback when addresses is empty', () {
      final map = {
        'state': 'stopped',
        'port': 8080,
        'addresses': <String>[],
      };

      final status = ServerStatus.fromMap(map);
      expect(status.primaryUrl, 'http://127.0.0.1:8080');
      expect(status.state.isStopped, isTrue);
    });

    test('parses unknown state as stopped', () {
      final status = ServerLifecycleState.fromString('random_unknown_state');
      expect(status, ServerLifecycleState.stopped);
    });

    test('serializes to map correctly', () {
      const status = ServerStatus(
        state: ServerLifecycleState.running,
        port: 9090,
        addresses: ['127.0.0.1'],
        activeClients: 1,
        activeStreams: 1,
        bytesTransferred: 500,
      );

      final map = status.toMap();
      expect(map['state'], 'running');
      expect(map['port'], 9090);
      expect(map['addresses'], ['127.0.0.1']);
      expect(map['activeClients'], 1);
    });
  });

  group('MediaItem Model', () {
    test('parses video media item and formats size', () {
      final map = {
        'id': 'saf_12345',
        'name': 'BigBuckBunny.mp4',
        'mimeType': 'video/mp4',
        'size': 157286400, // 150 MB
        'type': 'video',
        'source': 'saf',
        'available': true,
      };

      final item = MediaItem.fromMap(map);

      expect(item.id, 'saf_12345');
      expect(item.name, 'BigBuckBunny.mp4');
      expect(item.mimeType, 'video/mp4');
      expect(item.isVideo, isTrue);
      expect(item.isAudio, isFalse);
      expect(item.formattedSize, '150.0 MB');
      expect(item.kind, MediaKind.video);
      expect(item.sourceType, MediaSourceType.saf);
    });

    test('formats GB size correctly', () {
      final map = {
        'id': 'saf_99999',
        'name': 'Movie4K.mkv',
        'mimeType': 'video/x-matroska',
        'size': 5368709120, // 5 GB
        'type': 'video',
        'source': 'saf',
        'available': true,
      };

      final item = MediaItem.fromMap(map);
      expect(item.formattedSize, '5.00 GB');
      expect(item.isVideo, isTrue);
    });

    // The server emits a server-relative path (ApiResponseBuilder), not an
    // absolute URL, so clients must be able to round-trip it unchanged.
    test('round-trips the relative thumbUrl used by the web and TV clients',
        () {
      final map = {
        'id': 'saf_12345',
        'name': 'Clip.mp4',
        'mimeType': 'video/mp4',
        'size': 2048,
        'type': 'video',
        'source': 'saf',
        'available': true,
        'thumbUrl': '/api/v1/thumb/saf_12345',
      };

      final item = MediaItem.fromMap(map);
      expect(item.thumbUrl, '/api/v1/thumb/saf_12345');
      expect(item.toMap()['thumbUrl'], '/api/v1/thumb/saf_12345');
    });

    test('omits thumbUrl when the server did not send one', () {
      final item = MediaItem.fromMap({
        'id': 'm2',
        'name': 'NoThumb.mp4',
        'mimeType': 'video/mp4',
        'size': 10,
        'type': 'video',
        'source': 'saf',
        'available': true,
      });

      expect(item.thumbUrl, isNull);
      expect(item.toMap().containsKey('thumbUrl'), isFalse);
    });
  });

  group('ConnectedClient Model', () {
    test('round-trips through toMap/fromMap', () {
      const original = ConnectedClient(
        ip: '192.168.1.10',
        label: 'Desktop',
        kind: 'browser',
        browser: 'Chrome',
        browserVersion: '120.0',
        platform: 'macOS',
        online: true,
        firstSeenMs: 1000,
        lastSeenMs: 2000,
        bytes: 4096,
        playing: [
          ConnectedPlayback(
            id: 'play_1',
            name: 'Video.mp4',
            startedAtMs: 1200,
            active: true,
            bytes: 2048,
          ),
        ],
      );

      final map = original.toMap();
      final back = ConnectedClient.fromMap(map);

      expect(back.ip, '192.168.1.10');
      expect(back.label, 'Desktop');
      expect(back.kind, 'browser');
      expect(back.browser, 'Chrome');
      expect(back.platform, 'macOS');
      expect(back.isBrowser, isTrue);
      expect(back.playing.length, 1);
      expect(back.playing.first.name, 'Video.mp4');
    });
  });

  group('ServerInfo Model', () {
    test('parses from map and serializes back', () {
      final map = {
        'serverName': 'LocalStream Phone',
        'version': '1.0.1',
        'port': 8080,
        'protocolVersion': 1,
      };

      final info = ServerInfo.fromMap(map);
      expect(info.name, 'LocalStream Phone');
      expect(info.version, '1.0.1');
      expect(info.port, 8080);
      expect(info.protocolVersion, 1);
    });

    // ApiResponseBuilder emits `name`, not `serverName`; docs/API.md says
    // `serverName`. The implementation is the source of truth.
    test('serializes using the field names the server actually emits', () {
      const info = ServerInfo(
        name: 'LocalStream',
        version: '1.0.1',
        port: 8080,
        protocolVersion: 1,
      );

      final out = info.toMap();
      expect(out['name'], 'LocalStream');
      expect(out['version'], '1.0.1');
      expect(out['serverVersion'], '1.0.1');
      expect(out['port'], 8080);
      expect(out['protocolVersion'], 1);
    });

    test('accepts the server `name` field', () {
      final info = ServerInfo.fromMap({
        'name': 'LocalStream Phone',
        'version': '1.0.1',
        'port': 8080,
        'protocolVersion': 1,
      });
      expect(info.name, 'LocalStream Phone');
    });
  });

  group('Folder & LibraryView Models', () {
    test('parses FolderEntry and LibraryView', () {
      final map = {
        'folders': [
          {'id': 'f1', 'name': 'Movies', 'itemCount': 5, 'subfolderCount': 1},
        ],
        'items': [
          {
            'id': 'm1',
            'name': 'Movie.mp4',
            'mimeType': 'video/mp4',
            'size': 1024,
            'type': 'video',
            'source': 'saf',
            'available': true,
          }
        ],
      };

      final view = LibraryView.fromMap(map);
      expect(view.folders.length, 1);
      expect(view.folders.first.name, 'Movies');
      expect(view.items.length, 1);
      expect(view.items.first.name, 'Movie.mp4');
    });
  });
}
