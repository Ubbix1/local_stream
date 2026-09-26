import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';

import '../../models/library_models.dart';
import '../../repositories/media_repository.dart';

class PlayerScreen extends StatefulWidget {
  const PlayerScreen({
    super.key,
    required this.repo,
    required this.baseUrl,
    required this.entry,
  });

  final MediaRepository repo;
  final String baseUrl;
  final MediaEntry entry;

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  VideoPlayerController? _controller;
  Timer? _autoHide;
  bool _ready = false;
  bool _failed = false;
  bool _controlsVisible = true;
  bool _isPlaying = false;
  Duration _position = Duration.zero;
  Duration? _duration;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _init());
  }

  Future<void> _init() async {
    final headers = <String, String>{
      if (widget.repo.api.token != null) 'X-LocalStream-Token': widget.repo.api.token!,
    };
    final controller = VideoPlayerController.networkUrl(
      Uri.parse('${widget.baseUrl}/api/v1/stream/${widget.entry.id}'),
      httpHeaders: headers,
    );
    _controller = controller;
    try {
      await controller.initialize();
      if (!mounted) return;
      setState(() {
        _ready = true;
        _duration = controller.value.duration;
      });
      controller.addListener(_onTick);
      await controller.play();
      _scheduleHide();
    } catch (_) {
      if (!mounted) return;
      setState(() => _failed = true);
    }
  }

  void _onTick() {
    final value = _controller?.value;
    if (value == null) return;
    if (!mounted) return;
    final playing = value.isPlaying;
    final position = value.position;
    final ended = value.isCompleted;
    if (playing != _isPlaying ||
        position != _position ||
        ended) {
      setState(() {
        _isPlaying = playing;
        _position = position;
      });
    }
    if (ended) {
      _toggleControls();
    }
  }

  void _scheduleHide() {
    _autoHide?.cancel();
    if (!_isPlaying) return;
    _autoHide = Timer(const Duration(seconds: 4), () {
      if (mounted) setState(() => _controlsVisible = false);
    });
  }

  void _toggleControls() {
    setState(() => _controlsVisible = !_controlsVisible);
    if (_controlsVisible) _scheduleHide();
  }

  void _togglePlay() async {
    final controller = _controller;
    if (controller == null) return;
    if (controller.value.isPlaying) {
      await controller.pause();
    } else {
      await controller.play();
    }
    _scheduleHide();
  }

  Future<void> _seekBy(int seconds) async {
    final controller = _controller;
    if (controller == null || !_ready) return;
    var target = controller.value.position + Duration(seconds: seconds);
    final duration = controller.value.duration;
    if (target < Duration.zero) target = Duration.zero;
    if (target > duration) target = duration;
    await controller.seekTo(target);
    _scheduleHide();
  }

  Future<void> _back() async {
    await _controller?.dispose();
    if (mounted) Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _autoHide?.cancel();
    final controller = _controller;
    if (controller != null) {
      controller.removeListener(_onTick);
      controller.dispose();
    }
    super.dispose();
  }

  String _fmt(Duration d) {
    final total = d.inSeconds;
    final h = total ~/ 3600;
    final m = (total % 3600) ~/ 60;
    final s = total % 60;
    String two(int v) => v.toString().padLeft(2, '0');
    return h > 0 ? '$h:${two(m)}:${two(s)}' : '$m:${two(s)}';
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final controller = _controller;
    return Scaffold(
      backgroundColor: Colors.black,
      body: Focus(
        autofocus: true,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (_ready && controller != null)
              GestureDetector(
                onTap: _toggleControls,
                child: Center(
                  child: AspectRatio(
                    aspectRatio: controller.value.aspectRatio,
                    child: VideoPlayer(controller),
                  ),
                ),
              )
            else if (_failed)
              Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.error_outline, size: 64, color: scheme.error),
                    const SizedBox(height: 16),
                    const Text('Could not play this file.'),
                    const SizedBox(height: 24),
                    FilledButton(onPressed: _back, child: const Text('Back')),
                  ],
                ),
              )
            else
              const Center(child: CircularProgressIndicator()),
            if (_ready && _controlsVisible)
              _buildControls(scheme),
          ],
        ),
      ),
    );
  }

  Widget _buildControls(ColorScheme scheme) {
    final duration = _duration ?? _controller?.value.duration;
    final progress = (duration != null && duration.inMilliseconds > 0)
        ? (_position.inMilliseconds / duration.inMilliseconds).clamp(0.0, 1.0)
        : 0.0;

    return Align(
      alignment: Alignment.bottomCenter,
      child: Container(
        margin: const EdgeInsets.all(20),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.72),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (duration != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(_fmt(_position)),
                    Text(_fmt(duration)),
                  ],
                ),
              ),
            if (duration != null)
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: progress,
                  minHeight: 4,
                  color: scheme.primary,
                  backgroundColor: scheme.surfaceContainerHighest,
                ),
              ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _ControlButton(
                  icon: Icons.replay_10,
                  label: 'Back 10s',
                  autofocus: true,
                  onActivate: () => _seekBy(-10),
                ),
                _ControlButton(
                  icon: _isPlaying ? Icons.pause_circle_filled : Icons.play_circle_filled,
                  label: _isPlaying ? 'Pause' : 'Play',
                  primary: true,
                  onActivate: _togglePlay,
                ),
                _ControlButton(
                  icon: Icons.forward_10,
                  label: 'Forward 10s',
                  onActivate: () => _seekBy(10),
                ),
                const SizedBox(width: 8),
                _ControlButton(
                  icon: Icons.arrow_back,
                  label: 'Back',
                  onActivate: _back,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ControlButton extends StatelessWidget {
  const _ControlButton({
    required this.icon,
    required this.label,
    required this.onActivate,
    this.primary = false,
    this.autofocus = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onActivate;
  final bool primary;
  final bool autofocus;

  @override
  Widget build(BuildContext context) {
    return Focus(
      autofocus: autofocus,
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
          final active = focused || primary;
          return InkWell(
            onTap: onActivate,
            borderRadius: BorderRadius.circular(12),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 100),
              margin: const EdgeInsets.symmetric(horizontal: 6),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color: active ? scheme.primary : Colors.white10,
                border: focused
                    ? Border.all(color: scheme.primary, width: 3)
                    : Border.all(color: Colors.white24, width: 1),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(icon,
                      size: 28, color: active ? scheme.onPrimary : Colors.white),
                  const SizedBox(height: 2),
                  Text(label, style: const TextStyle(color: Colors.white, fontSize: 10)),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}