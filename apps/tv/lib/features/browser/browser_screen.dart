import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:localstream_protocol/protocol.dart';

import '../../core/errors/app_error.dart';
import '../../repositories/media_repository.dart';
import '../player/player_screen.dart';

class _Level {
  _Level({required this.folders, required this.items, this.title});

  final List<FolderEntry> folders;
  final List<MediaItem> items;
  final String? title;
}

class BrowserScreen extends StatefulWidget {
  const BrowserScreen({super.key, required this.repo, required this.baseUrl});

  final MediaRepository repo;
  final String baseUrl;

  @override
  State<BrowserScreen> createState() => _BrowserScreenState();
}

class _BrowserScreenState extends State<BrowserScreen> {
  final List<_Level> _stack = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _loadRoot();
    });
  }

  Future<void> _loadRoot() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final view = await widget.repo.root();
      if (!mounted) return;
      setState(() {
        _stack
          ..clear()
          ..add(_Level(folders: view.folders, items: view.items));
        _loading = false;
      });
    } on AppError catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = e.userMessage;
      });
    }
  }

  Future<void> _openFolder(FolderEntry folder) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final view = await widget.repo.openFolder(folder.id);
      if (!mounted) return;
      setState(() {
        _stack.add(_Level(
          folders: view.folders,
          items: view.items,
          title: folder.name,
        ));
        _loading = false;
      });
    } on AppError catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = e.userMessage;
      });
    }
  }

  Future<void> _goUp() async {
    if (_stack.length <= 1) return;
    setState(() => _stack.removeLast());
  }

  Future<void> _open(MediaItem entry) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PlayerScreen(
          repo: widget.repo,
          baseUrl: widget.baseUrl,
          entry: entry,
        ),
      ),
    );
  }

  void _handleBack() {
    if (_stack.length > 1) {
      _goUp();
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope<void>(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _handleBack();
      },
      child: Scaffold(
        body: SafeArea(
          child: Focus(
            autofocus: true,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeader(),
                Expanded(child: _buildBody()),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    final scheme = Theme.of(context).colorScheme;
    final breadcrumbs = <String>[
      'Library',
      ..._stack.map((l) => l.title ?? '').where((s) => s.isNotEmpty)
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 24, 32, 16),
      child: Row(
        children: [
          IconButton(
            onPressed: _loadRoot,
            tooltip: 'Go to root',
            icon: const Icon(Icons.video_library_outlined),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  breadcrumbs.join(' / '),
                  style: Theme.of(context).textTheme.titleLarge,
                  overflow: TextOverflow.ellipsis,
                ),
                if (_stack.isNotEmpty)
                  Text(
                    '${_stack.last.folders.length} folders · ${_stack.last.items.length} items',
                    style: Theme.of(context)
                        .textTheme
                        .bodyMedium
                        ?.copyWith(color: scheme.onSurfaceVariant),
                  ),
              ],
            ),
          ),
          Focus(
            onKeyEvent: (node, event) {
              if (event is KeyDownEvent &&
                  (event.logicalKey == LogicalKeyboardKey.gameButtonB ||
                      event.logicalKey == LogicalKeyboardKey.goBack)) {
                _handleBack();
                return KeyEventResult.handled;
              }
              return KeyEventResult.ignored;
            },
            child: const SizedBox.shrink(),
          ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline,
                size: 56, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: 16),
            Text(_error!, textAlign: TextAlign.center),
            const SizedBox(height: 24),
            FilledButton(onPressed: _loadRoot, child: const Text('Retry')),
          ],
        ),
      );
    }
    final level = _stack.isEmpty ? null : _stack.last;
    if (level == null || (level.folders.isEmpty && level.items.isEmpty)) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.folder_off_outlined,
                size: 56,
                color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(height: 16),
            const Text('This folder is empty'),
            const SizedBox(height: 24),
            if (_stack.length > 1)
              FilledButton(onPressed: _goUp, child: const Text('Go back')),
          ],
        ),
      );
    }
    final cells = <Widget>[
      for (final folder in level.folders)
        _FolderTile(folder: folder, onActivate: () => _openFolder(folder)),
      for (final item in level.items)
        _MediaTile(
            repo: widget.repo, entry: item, onActivate: () => _open(item)),
    ];
    return FocusTraversalGroup(
      child: GridView.builder(
        padding: const EdgeInsets.fromLTRB(32, 8, 32, 32),
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 280,
          mainAxisSpacing: 16,
          crossAxisSpacing: 16,
          childAspectRatio: 1.55,
        ),
        itemCount: cells.length,
        itemBuilder: (context, index) => cells[index],
      ),
    );
  }
}

class _FocusCard extends StatelessWidget {
  const _FocusCard({required this.child, required this.onActivate});

  final Widget child;
  final VoidCallback onActivate;

  @override
  Widget build(BuildContext context) {
    return Focus(
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.numpadEnter)) {
          onActivate();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          final focused = Focus.of(context).hasFocus;
          final scheme = Theme.of(context).colorScheme;
          return AnimatedContainer(
            duration: const Duration(milliseconds: 120),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: focused ? scheme.primary : Colors.transparent,
                width: 3,
              ),
              boxShadow: focused
                  ? [
                      BoxShadow(
                        color: scheme.primary.withValues(alpha: 0.35),
                        blurRadius: 18,
                      ),
                    ]
                  : null,
            ),
            child: Material(
              color: focused
                  ? scheme.surfaceContainerHigh
                  : scheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: onActivate,
                child: child,
              ),
            ),
          );
        },
      ),
    );
  }
}

class _FolderTile extends StatelessWidget {
  const _FolderTile({required this.folder, required this.onActivate});

  final FolderEntry folder;
  final VoidCallback onActivate;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return _FocusCard(
      onActivate: onActivate,
      child: Row(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Icon(Icons.folder, size: 42, color: scheme.primary),
          ),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  folder.name,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  '${folder.itemCount} items',
                  style: Theme.of(context)
                      .textTheme
                      .bodySmall
                      ?.copyWith(color: scheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MediaTile extends StatelessWidget {
  const _MediaTile(
      {required this.repo, required this.entry, required this.onActivate});

  final MediaRepository repo;
  final MediaItem entry;
  final VoidCallback onActivate;

  IconData get _icon => switch (entry.type) {
        'video' => Icons.movie,
        'audio' => Icons.music_note,
        'image' => Icons.image,
        _ => Icons.insert_drive_file,
      };

  String _fmtDuration(int ms) {
    final total = (ms / 1000).round();
    final h = total ~/ 3600;
    final m = (total % 3600) ~/ 60;
    final s = total % 60;
    String two(int v) => v.toString().padLeft(2, '0');
    return h > 0 ? '$h:${two(m)}:${two(s)}' : '$m:${two(s)}';
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return _FocusCard(
      onActivate: onActivate,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: Stack(
              fit: StackFit.expand,
              children: [
                Container(
                  color: scheme.surfaceContainerHighest,
                  child: _thumbnail(scheme),
                ),
                if (entry.durationMs != null)
                  Positioned(
                    right: 8,
                    bottom: 8,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.72),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        _fmtDuration(entry.durationMs!),
                        style:
                            const TextStyle(color: Colors.white, fontSize: 11),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(10),
            child: Text(
              entry.name,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
        ],
      ),
    );
  }

  Widget _thumbnail(ColorScheme scheme) {
    final thumbUrl = entry.thumbUrl;
    if (thumbUrl == null) {
      return Center(
        child: Icon(_icon, size: 40, color: scheme.onSurfaceVariant),
      );
    }
    final headers = <String, String>{
      if (repo.api.token != null) 'X-LocalStream-Token': repo.api.token!,
    };
    return Image.network(
      '${repo.api.baseUrl}$thumbUrl',
      headers: headers,
      fit: BoxFit.cover,
      errorBuilder: (_, __, ___) =>
          Center(child: Icon(_icon, size: 40, color: scheme.onSurfaceVariant)),
      loadingBuilder: (context, child, progress) {
        if (progress == null) return child;
        return Center(
          child: Icon(_icon, size: 40, color: scheme.onSurfaceVariant),
        );
      },
    );
  }
}
