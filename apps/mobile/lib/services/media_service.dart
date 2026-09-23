import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/media_item.dart';
import 'platform_bridge.dart';

class MediaService extends ChangeNotifier {
  final PlatformBridge _bridge = PlatformBridge();
  StreamSubscription? _eventSub;

  List<MediaItem> _items = [];
  bool _isLoading = false;
  String _searchQuery = '';
  String _selectedFilter = 'all'; // "all", "video", "audio", "image"
  String? _importingFileName;
  int _importedBytes = 0;
  int _importTotalBytes = -1;

  List<MediaItem> get items => _items;
  bool get isLoading => _isLoading;
  String get searchQuery => _searchQuery;
  String get selectedFilter => _selectedFilter;
  String? get importingFileName => _importingFileName;
  int get importedBytes => _importedBytes;
  int get importTotalBytes => _importTotalBytes;
  double? get importProgress => _importTotalBytes > 0
      ? (_importedBytes / _importTotalBytes).clamp(0.0, 1.0)
      : null;

  List<MediaItem> get filteredItems {
    return _items.where((item) {
      if (_selectedFilter != 'all' && item.type != _selectedFilter) {
        return false;
      }
      if (_searchQuery.isNotEmpty &&
          !item.name.toLowerCase().contains(_searchQuery.toLowerCase())) {
        return false;
      }
      return true;
    }).toList();
  }

  MediaService() {
    _init();
  }

  void _init() {
    loadMedia();
    _eventSub = _bridge.events.listen((event) {
      if (event['type'] == 'library_changed') {
        loadMedia();
      } else if (event['type'] == 'import_progress') {
        _importingFileName = event['name']?.toString();
        _importedBytes = (event['bytes'] as num?)?.toInt() ?? 0;
        _importTotalBytes = (event['totalBytes'] as num?)?.toInt() ?? -1;
        if (event['done'] == true || event['error'] == true) {
          _importingFileName = null;
          loadMedia();
        }
        notifyListeners();
      }
    });
  }

  void setSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  void setFilter(String filter) {
    _selectedFilter = filter;
    notifyListeners();
  }

  Future<void> loadMedia() async {
    _isLoading = true;
    notifyListeners();
    try {
      _items = await _bridge.listFiles();
    } catch (_) {
      _items = [];
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<int> addSafFolder(String treeUri) async {
    final count = await _bridge.addSafFolder(treeUri);
    await loadMedia();
    return count;
  }

  Future<int> pickMediaFiles() async {
    final count = await _bridge.pickMediaFiles();
    await loadMedia();
    return count;
  }

  Future<bool> removeMedia(String id) async {
    final success = await _bridge.removeMediaItem(id);
    if (success) {
      _items.removeWhere((item) => item.id == id);
      notifyListeners();
    }
    return success;
  }

  Future<MediaItem?> importMedia(String uri, {String? targetName}) async {
    final item = await _bridge.importMediaItem(uri, targetName: targetName);
    if (item != null) {
      await loadMedia();
    }
    return item;
  }

  @override
  void dispose() {
    _eventSub?.cancel();
    super.dispose();
  }
}
