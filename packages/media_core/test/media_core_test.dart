import 'package:localstream_media_core/media_core.dart';
import 'package:test/test.dart';

/// In-memory [MediaSource] used to prove the abstraction is implementable
/// without any platform storage behind it.
class _FakeSource implements MediaSource {
  _FakeSource(this.id, this.name, this._bytes);

  @override
  final String id;
  @override
  final String name;
  @override
  String? get mimeType => 'video/mp4';

  final List<int> _bytes;
  bool closed = false;

  @override
  Future<int?> length() async => _bytes.length;

  @override
  Future<List<int>> readRange(int start, int end) async =>
      _bytes.sublist(start, end + 1);

  @override
  Future<void> close() async => closed = true;
}

class _FakeRepository implements MediaRepository {
  final Map<String, MediaSource> _byParent = {};
  final Map<String, MediaSource> _byId = {};

  @override
  Future<void> add(MediaSource source) async {
    _byId[source.id] = source;
    _byParent[source.id] = source;
  }

  @override
  Future<void> remove(String id) async {
    _byId.remove(id);
    _byParent.remove(id);
  }

  @override
  Future<List<Object>> list({String? parentId}) async =>
      _byParent.values.where((s) => s.id.startsWith(parentId ?? '')).toList();

  @override
  Future<MediaSource?> open(String id) async => _byId[id];
}

void main() {
  group('MediaRepository contract', () {
    late _FakeRepository repository;

    setUp(() => repository = _FakeRepository());

    test('add, open and list round-trip a source', () async {
      final source = _FakeSource('a.mp4', 'a.mp4', [1, 2, 3, 4, 5]);
      await repository.add(source);

      expect(await repository.list(), hasLength(1));
      expect((await repository.open('a.mp4'))?.name, 'a.mp4');
    });

    test('open returns null for an unknown id', () async {
      expect(await repository.open('missing.mp4'), isNull);
    });

    test('remove drops the source', () async {
      await repository.add(_FakeSource('a.mp4', 'a.mp4', [1]));
      await repository.remove('a.mp4');

      expect(await repository.list(), isEmpty);
      expect(await repository.open('a.mp4'), isNull);
    });

    test('list scopes to the requested parent', () async {
      await repository.add(_FakeSource('root/a.mp4', 'a.mp4', [1]));
      await repository.add(_FakeSource('other/b.mp4', 'b.mp4', [1]));

      expect(await repository.list(parentId: 'root/'), hasLength(1));
    });
  });

  group('MediaSource contract', () {
    test('length, readRange and close are implementable', () async {
      final source = _FakeSource('a.mp4', 'a.mp4', [10, 20, 30, 40, 50]);

      expect(await source.length(), 5);
      expect(await source.readRange(1, 3), [20, 30, 40]);
      expect(source.mimeType, isNotNull);

      await source.close();
      expect(source.closed, isTrue);
    });
  });
}
