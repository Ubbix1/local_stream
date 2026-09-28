import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

import '../../core/errors/app_error.dart';
import '../../core/network/api_client.dart';
import '../../repositories/media_repository.dart';
import '../../services/discovery_service.dart';
import '../../services/server_store.dart';
import '../../services/update_service.dart';
import '../browser/browser_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _store = ServerStore();
  final _addressController = TextEditingController();
  final _discovery = DiscoveryService();
  final _updateService = UpdateService();
  final _foundServers = <DiscoveredServer>[];
  final _probedKeys = <String>{};

  StreamSubscription<DiscoveredServer>? _discoverySub;
  Timer? _scanTimer;
  bool _scanning = false;

  String? _savedAddress;
  bool _configuring = false;
  bool _busy = false;
  String? _error;

  ApiClient? _api;

  @override
  void initState() {
    super.initState();
    _updateService.onChanged = _onUpdateChanged;
    _updateService.init();
    _loadSaved();
  }

  void _onUpdateChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _updateService.onChanged = null;
    _scanTimer?.cancel();
    _discoverySub?.cancel();
    _discovery.dispose();
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
    if (_configuring) await _startDiscovery();
  }

  Future<void> _startDiscovery() async {
    if (!DiscoveryService.supported) return;
    _scanTimer?.cancel();
    setState(() {
      _scanning = true;
      _foundServers.clear();
      _probedKeys.clear();
    });
    _discoverySub?.cancel();
    _discoverySub = _discovery.stream.listen(_onDiscovered);
    await _discovery.start();
    if (!mounted) return;
    _scanTimer = Timer(const Duration(seconds: 6), () {
      if (mounted) setState(() => _scanning = false);
    });
  }

  Future<void> _onDiscovered(DiscoveredServer server) async {
    if (!_probedKeys.add(server.baseUrl)) return;
    final verified = await _probe(server.baseUrl);
    if (!mounted || verified == null) return;
    setState(() => _foundServers.add(verified));
  }

  Future<DiscoveredServer?> _probe(String baseUrl) async {
    try {
      final res = await http
          .get(Uri.parse('$baseUrl/api/v1/info'))
          .timeout(const Duration(milliseconds: 2500));
      if (res.statusCode != 200) return null;
      final body = jsonDecode(res.body);
      if (body is! Map<String, dynamic> || body['name'] != 'LocalStream') {
        return null;
      }
      final uri = Uri.parse(baseUrl);
      final device = (body['deviceName'] as String?)?.trim();
      final version = (body['serverVersion'] as String?)?.trim() ?? '';
      final label = (device == null || device.isEmpty)
          ? 'LocalStream server'
          : (version.isEmpty ? device : '$device \u00b7 v$version');
      return DiscoveredServer(
        name: label,
        host: uri.host,
        port: uri.port,
      );
    } catch (_) {
      return null;
    }
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
      setState(() =>
          _error = 'Enter the server address, e.g. http://192.168.1.20:8080');
      return;
    }
    if (!address.startsWith('http://') && !address.startsWith('https://')) {
      address = 'http://$address';
    }
    final clean = address.endsWith('/')
        ? address.substring(0, address.length - 1)
        : address;
    await _connectTo(clean);
  }

  Future<void> _connectTo(String clean) async {
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
        child: Column(
          children: [
            if (_updateService.updateAvailable && !_updateService.dismissed)
              _buildUpdateBanner(),
            Expanded(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 900),
                  child: _configuring ? _buildSetup() : _buildLauncher(),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildUpdateBanner() {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.primaryContainer,
      child: Row(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 6),
            child: Text(
              'Update available: ${_updateService.latestVersion}',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: scheme.onPrimaryContainer,
              ),
            ),
          ),
          const Spacer(),
          TextButton(
            onPressed: () => _showUpdateDialog(context),
            child: const Text('How to update'),
          ),
          TextButton(
            onPressed: _updateService.dismiss,
            child: const Text('Later'),
          ),
        ],
      ),
    );
  }

  Future<void> _showUpdateDialog(BuildContext context) async {
    final url = _updateService.latestAssetUrl.isNotEmpty
        ? _updateService.latestAssetUrl
        : _updateService.latestReleaseUrl;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: Theme.of(context).colorScheme.surface,
        title: Text('Update available: ${_updateService.latestVersion}'),
        content: Text(url.isEmpty
            ? 'A newer build is available. Open the LocalStream GitHub releases page on a computer and sideload the Android TV APK onto this TV.'
            : 'Download the Android TV APK from the link below on a computer, transfer it to this TV, and sideload it.\n\n$url'),
        actions: [
          if (url.isNotEmpty)
            TextButton(
              onPressed: () async {
                await Clipboard.setData(ClipboardData(text: url));
                _updateService.dismiss();
                if (dialogContext.mounted) {
                  Navigator.pop(dialogContext);
                }
              },
              child: const Text('Copy link'),
            ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Later'),
          ),
        ],
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
          style: Theme.of(context)
              .textTheme
              .bodyLarge
              ?.copyWith(color: scheme.onSurfaceVariant),
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
              onActivate: () {
                setState(() => _configuring = true);
                _startDiscovery();
              },
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
          const SizedBox(height: 28),
          Row(
            children: [
              Expanded(
                child: Text(
                  'On this network',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              TextButton.icon(
                onPressed: DiscoveryService.supported ? _startDiscovery : null,
                icon: const Icon(Icons.refresh),
                label: const Text('Scan'),
              ),
            ],
          ),
          const SizedBox(height: 4),
          if (_scanning)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Center(
                child: SizedBox(
                  width: 28,
                  height: 28,
                  child: CircularProgressIndicator(strokeWidth: 3),
                ),
              ),
            )
          else if (_foundServers.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                DiscoveryService.supported
                    ? 'No LocalStream servers found yet \u2014 make sure the '
                        'server app is running on the same Wi-Fi or hotspot, '
                        'or enter its address below.'
                    : 'Nearby-server discovery is unavailable on this '
                        'device. Enter the server address below.',
                style: Theme.of(context)
                    .textTheme
                    .bodyMedium
                    ?.copyWith(color: scheme.onSurfaceVariant),
                textAlign: TextAlign.center,
              ),
            ),
          if (_foundServers.isNotEmpty) ...[
            const SizedBox(height: 8),
            for (final server in _foundServers)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _ServerTile(
                  server: server,
                  enabled: !_busy,
                  onActivate: () => _connectTo(server.baseUrl),
                ),
              ),
          ],
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
                  Icon(icon,
                      size: 32,
                      color: focused ? scheme.onPrimary : scheme.onSurface),
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

/// Remote-friendly tile for a discovered server, showing device name + URL.
class _ServerTile extends StatelessWidget {
  const _ServerTile({
    required this.server,
    required this.onActivate,
    this.enabled = true,
  });

  final DiscoveredServer server;
  final VoidCallback onActivate;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return Focus(
      onKeyEvent: (node, event) {
        if (enabled &&
            event is KeyDownEvent &&
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
            onTap: enabled ? onActivate : null,
            borderRadius: BorderRadius.circular(14),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(14),
                color: focused ? scheme.primary : scheme.surfaceContainerHigh,
                border: Border.all(
                  color: focused ? scheme.primary : scheme.outlineVariant,
                  width: 2,
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.tv,
                    size: 28,
                    color: focused ? scheme.onPrimary : scheme.onSurface,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          server.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                            color:
                                focused ? scheme.onPrimary : scheme.onSurface,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          server.baseUrl,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 13,
                            color: focused
                                ? scheme.onPrimary.withValues(alpha: 0.8)
                                : scheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Icon(
                    Icons.chevron_right,
                    size: 28,
                    color: focused ? scheme.onPrimary : scheme.onSurfaceVariant,
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
