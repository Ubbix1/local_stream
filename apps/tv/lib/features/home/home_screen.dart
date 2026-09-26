import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/errors/app_error.dart';
import '../../core/network/api_client.dart';
import '../../repositories/media_repository.dart';
import '../../services/server_store.dart';
import '../browser/browser_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _store = ServerStore();
  final _addressController = TextEditingController();

  String? _savedAddress;
  bool _configuring = false;
  bool _busy = false;
  String? _error;

  ApiClient? _api;

  @override
  void initState() {
    super.initState();
    _loadSaved();
  }

  @override
  void dispose() {
    _addressController.dispose();
    super.dispose();
  }

  Future<void> _loadSaved() async {
    final address = await _store.address();
    if (!mounted) return;
    setState(() {
      _savedAddress = address;
      _configuring = address == null;
      _addressController.text = address ?? '';
    });
  }

  Future<void> _openBrowser() async {
    final api = _api;
    if (api == null) return;
    final repo = MediaRepository(api);
    setState(() => _error = null);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => BrowserScreen(repo: repo, baseUrl: api.baseUrl),
      ),
    );
  }

  Future<void> _connect() async {
    var address = _addressController.text.trim();
    if (address.isEmpty) {
      setState(() => _error = 'Enter the server address, e.g. http://192.168.1.20:8080');
      return;
    }
    if (!address.startsWith('http://') && !address.startsWith('https://')) {
      address = 'http://$address';
    }
    final clean = address.endsWith('/') ? address.substring(0, address.length - 1) : address;
    setState(() {
      _busy = true;
      _error = null;
    });
    final api = ApiClient(clean);
    try {
      final repo = MediaRepository(api);
      final pin = await repo.pinRequired();
      await _store.saveAddress(clean);
      if (!mounted) return;
      setState(() {
        _savedAddress = clean;
        _api = api;
        _busy = false;
      });
      if (pin) {
        final unlocked = await _promptPin(repo);
        if (!mounted) return;
        if (unlocked) await _openBrowser();
      } else {
        if (!mounted) return;
        await _openBrowser();
      }
    } on AppError catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = e.userMessage;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = 'Could not connect to $clean';
      });
    }
  }

  Future<bool> _promptPin(MediaRepository repo) async {
    final controller = TextEditingController();
    var failed = false;
    final unlocked = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('Server requires a PIN'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: controller,
                autofocus: true,
                obscureText: true,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  hintText: 'Enter the PIN shown in the server app',
                  errorText: failed ? 'Wrong PIN — try again.' : null,
                ),
                onSubmitted: (_) async {
                  final ok = await _tryUnlock(
                    repo,
                    controller,
                    () => failed = true,
                  );
                  if (ok && ctx.mounted) Navigator.of(ctx).pop(true);
                },
              ),
            ],
          ),
          actions: [
            FilledButton(
              autofocus: true,
              onPressed: () async {
                final ok = await _tryUnlock(
                  repo,
                  controller,
                  () => failed = true,
                );
                if (ok && ctx.mounted) Navigator.of(ctx).pop(true);
              },
              child: const Text('Unlock'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
    return unlocked ?? false;
  }

  Future<bool> _tryUnlock(
    MediaRepository repo,
    TextEditingController controller,
    VoidCallback markFailed,
  ) async {
    try {
      await repo.verifyPin(controller.text.trim());
      await _store.saveToken(repo.api.token);
      return true;
    } on AppError catch (e) {
      if (e.code == ErrorCode.permissionDenied) {
        markFailed();
        controller.clear();
      } else {
        markFailed();
      }
      return false;
    } catch (_) {
      markFailed();
      return false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 900),
            child: _configuring ? _buildSetup() : _buildLauncher(),
          ),
        ),
      ),
    );
  }

  Widget _buildLauncher() {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.ondemand_video, size: 72, color: scheme.primary),
        const SizedBox(height: 16),
        Text(
          'LocalStream TV',
          style: Theme.of(context).textTheme.headlineMedium,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          'Streaming from\n$_savedAddress',
          style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: scheme.onSurfaceVariant),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 40),
        Wrap(
          spacing: 24,
          runSpacing: 16,
          children: [
            _FocusButton(
              icon: Icons.video_library,
              label: 'Browse Library',
              autofocus: true,
              onActivate: _openBrowser,
            ),
            _FocusButton(
              icon: Icons.settings,
              label: 'Change server',
              onActivate: () => setState(() => _configuring = true),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildSetup() {
    final scheme = Theme.of(context).colorScheme;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Icon(Icons.ondemand_video, size: 64, color: scheme.primary),
          const SizedBox(height: 16),
          Text(
            'Connect to a LocalStream server',
            style: Theme.of(context).textTheme.headlineSmall,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 32),
          Focus(
            autofocus: true,
            child: TextField(
              controller: _addressController,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(
                labelText: 'Server address',
                hintText: 'http://192.168.1.20:8080',
                border: OutlineInputBorder(),
              ),
              onSubmitted: (_) => _connect(),
            ),
          ),
          const SizedBox(height: 16),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Text(
                _error!,
                style: TextStyle(color: scheme.error),
                textAlign: TextAlign.center,
              ),
            ),
          if (_busy)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: CircularProgressIndicator()),
            )
          else
            FilledButton.icon(
              onPressed: _connect,
              icon: const Icon(Icons.link),
              label: const Padding(
                padding: EdgeInsets.all(16),
                child: Text('Connect'),
              ),
            ),
          if (_savedAddress != null) ...[
            const SizedBox(height: 12),
            TextButton(
              onPressed: () {
                setState(() {
                  _configuring = false;
                  _error = null;
                });
              },
              child: const Text('Cancel'),
            ),
          ],
        ],
      ),
    );
  }
}

/// Oversized remote-friendly tile with focus highlight and enter activation.
class _FocusButton extends StatelessWidget {
  const _FocusButton({
    required this.icon,
    required this.label,
    required this.onActivate,
    this.autofocus = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onActivate;
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
          return InkWell(
            onTap: onActivate,
            borderRadius: BorderRadius.circular(16),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: 320,
              padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 32),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                color: focused ? scheme.primary : scheme.surfaceContainerHigh,
                border: Border.all(
                  color: focused ? scheme.primary : scheme.outlineVariant,
                  width: 2,
                ),
                boxShadow: focused
                    ? [
                        BoxShadow(
                          color: scheme.primary.withValues(alpha: 0.45),
                          blurRadius: 24,
                          spreadRadius: 2,
                        ),
                      ]
                    : null,
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(icon, size: 32, color: focused ? scheme.onPrimary : scheme.onSurface),
                  const SizedBox(width: 12),
                  Flexible(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: focused ? scheme.onPrimary : scheme.onSurface,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}