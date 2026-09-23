import 'package:flutter_test/flutter_test.dart';
import 'package:localstream_mobile/models/media_item.dart';
import 'package:localstream_mobile/models/server_status.dart';

void main() {
  group('ServerStatus Model Tests', () {
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
  });

  group('MediaItem Model Tests', () {
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
  });
}
