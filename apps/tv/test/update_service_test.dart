import 'package:flutter_test/flutter_test.dart';
import 'package:localstream_tv/services/update_service.dart';

void main() {
  group('UpdateService.isNewer', () {
    test('same version is not newer', () {
      expect(UpdateService.isNewer('v1.0.0', '1.0.0'), isFalse);
    });

    test('"1.0" and "v1.0.0" are the same version', () {
      expect(UpdateService.isNewer('v1.0.0', '1.0'), isFalse);
    });

    test('newer patch is detected', () {
      expect(UpdateService.isNewer('v1.0.1', '1.0.0'), isTrue);
    });

    test('older or equal release is not newer', () {
      expect(UpdateService.isNewer('v1.0.0', '1.1.0'), isFalse);
      expect(UpdateService.isNewer('v1.0.0', 'v1.0.0'), isFalse);
    });

    test('prerelease suffix is ignored numerically', () {
      expect(UpdateService.isNewer('v1.1.0-beta', '1.0.0'), isTrue);
    });

    test('major bump wins', () {
      expect(UpdateService.isNewer('v2.0.0', '1.9.9'), isTrue);
    });
  });

  group('UpdateService.versionSegments', () {
    test('normalizes missing parts', () {
      expect(UpdateService.versionSegments('1'), [1, 0, 0]);
      expect(UpdateService.versionSegments('1.2'), [1, 2, 0]);
      expect(UpdateService.versionSegments('v1.2.3'), [1, 2, 3]);
    });
  });
}