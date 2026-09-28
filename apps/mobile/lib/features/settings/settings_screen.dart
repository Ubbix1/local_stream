import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../services/native_bridge.dart';
import '../../services/update_service.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final updates = context.watch<UpdateService>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings & Diagnostics',
            style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Android 13-16 System & Permissions Card
          Card(
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.android,
                          color: Colors.greenAccent, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'Android Architecture (API 33 - 36)',
                        style: theme.textTheme.titleSmall
                            ?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _buildSystemRow('Foreground Service',
                      'Persistent dataSync service with notification'),
                  _buildSystemRow('UI Independence',
                      'Server keeps streaming when Flutter UI is closed'),
                  _buildSystemRow('Storage Access',
                      'Storage Access Framework (SAF) + Content URIs'),
                  _buildSystemRow('Android Share',
                      'Generic ACTION_SEND / ACTION_SEND_MULTIPLE receiver'),
                  _buildSystemRow('Streaming Engine',
                      'Pure Kotlin ServerSocket + HTTP Range (206)'),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          const _AccessPinCard(),

          const SizedBox(height: 16),

          // About LocalStream
          Card(
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.info_outline,
                          color: colorScheme.primary, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'About LocalStream',
                        style: theme.textTheme.titleSmall
                            ?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'LocalStream turns your Android phone into a high-performance local media server for your TV, browser, or any other device on your Wi-Fi or hotspot.',
                    style: TextStyle(fontSize: 13, height: 1.4),
                  ),
                  const SizedBox(height: 12),
                  const Divider(),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('App Version',
                          style: TextStyle(fontSize: 12, color: Colors.grey)),
                      Text(
                        updates.installedVersion.isEmpty
                            ? '—'
                            : updates.installedVersion,
                        style: const TextStyle(
                            fontSize: 12, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      if (updates.checking)
                        const SizedBox(
                          width: 12,
                          height: 12,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      else
                        Icon(
                          updates.error != null
                              ? Icons.error_outline
                              : (updates.updateAvailable
                                  ? Icons.arrow_circle_up
                                  : Icons.check_circle_outline),
                          size: 14,
                          color: updates.error != null
                              ? colorScheme.error
                              : (updates.updateAvailable
                                  ? colorScheme.primary
                                  : Colors.greenAccent),
                        ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          updates.checking
                              ? 'Checking for updates…'
                              : updates.error != null
                                  ? '${updates.error}'
                                  : updates.updateAvailable
                                      ? 'New version ${updates.latestVersion} available'
                                      : (updates.installedVersion.isEmpty
                                          ? 'Update checks every launch'
                                          : 'Up to date'),
                          style: const TextStyle(fontSize: 11),
                        ),
                      ),
                      TextButton(
                        onPressed: updates.checking ? null : updates.refresh,
                        child: const Text('Check for updates'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  const Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Protocol Version',
                          style: TextStyle(fontSize: 12, color: Colors.grey)),
                      Text('v1 (HTTP /api/v1)',
                          style: TextStyle(
                              fontSize: 12, fontWeight: FontWeight.bold)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  const Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Browser Client',
                          style: TextStyle(fontSize: 12, color: Colors.grey)),
                      Text('SPA + folders/thumbnails',
                          style: TextStyle(
                              fontSize: 12, fontWeight: FontWeight.bold)),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSystemRow(String title, String desc) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.check_circle_outline,
              size: 14, color: Colors.greenAccent),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(
                        fontSize: 12, fontWeight: FontWeight.bold)),
                Text(desc,
                    style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Web/PIN access card: optionally require a PIN before the web client or API
/// can be used by any other device on the network.
class _AccessPinCard extends StatefulWidget {
  const _AccessPinCard();

  @override
  State<_AccessPinCard> createState() => _AccessPinCardState();
}

class _AccessPinCardState extends State<_AccessPinCard> {
  final NativeBridge _bridge = NativeBridge();
  bool _loading = true;
  bool _pinRequired = false;

  @override
  void initState() {
    super.initState();
    _loadState();
  }

  Future<void> _loadState() async {
    try {
      final state = await _bridge.getPinState();
      if (!mounted) return;
      setState(() {
        _pinRequired = state['required'] == true;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  Future<void> _toggle(bool enabled) async {
    if (enabled == _pinRequired) return;

    if (enabled) {
      final pin = await _promptForPin();
      if (pin == null || pin.isEmpty) return;
      try {
        await _bridge.setAccessPin(pin);
        if (!mounted) return;
        setState(() => _pinRequired = true);
        _toast('Web access PIN enabled');
      } on PlatformException catch (e) {
        _toast(e.message ?? 'Could not enable PIN');
      }
    } else {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Disable web access PIN?'),
          content: const Text(
              'Anyone on your network will be able to browse and play your library again.'),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Disable')),
          ],
        ),
      );
      if (confirmed != true) return;
      try {
        await _bridge.clearAccessPin();
        if (!mounted) return;
        setState(() => _pinRequired = false);
        _toast('Web access PIN disabled');
      } on PlatformException catch (e) {
        _toast(e.message ?? 'Could not disable PIN');
      }
    }
  }

  Future<String?> _promptForPin() async {
    final pinController = TextEditingController();
    final confirmController = TextEditingController();
    final formKey = GlobalKey<FormState>();
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Set a web access PIN'),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Visitors must enter this PIN before they can open the web client or the API.',
                style: TextStyle(fontSize: 12),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: pinController,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 6,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'PIN (4-6 digits)',
                  border: OutlineInputBorder(),
                  counterText: '',
                ),
                validator: (value) {
                  final v = value?.trim() ?? '';
                  if (v.length < 4) return 'Use at least 4 digits';
                  return null;
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: confirmController,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 6,
                decoration: const InputDecoration(
                  labelText: 'Confirm PIN',
                  border: OutlineInputBorder(),
                  counterText: '',
                ),
                validator: (value) {
                  if (value?.trim() != pinController.text.trim()) {
                    return 'PINs do not match';
                  }
                  return null;
                },
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel')),
          FilledButton(
            onPressed: () {
              if (formKey.currentState?.validate() ?? false) {
                Navigator.pop(context, pinController.text.trim());
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
    return result;
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                    _pinRequired
                        ? Icons.lock_outline
                        : Icons.lock_open_outlined,
                    color: _pinRequired ? Colors.amberAccent : Colors.grey,
                    size: 20),
                const SizedBox(width: 8),
                Text(
                  'Web Access PIN',
                  style: theme.textTheme.titleSmall
                      ?.copyWith(fontWeight: FontWeight.bold),
                ),
              ],
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Require a PIN for browser access'),
              subtitle: const Text(
                'Gates the web client, streaming and the /api/v1 endpoints from other devices on your network.',
                style: TextStyle(fontSize: 11),
              ),
              value: _pinRequired,
              onChanged: _loading ? null : (value) => _toggle(value),
            ),
          ],
        ),
      ),
    );
  }
}
