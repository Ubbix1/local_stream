import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/connected_client.dart';
import '../../services/client_store.dart';
import '../../services/platform_bridge.dart';
import '../../services/server_service.dart';
import '../../widgets/liquid_glass_button.dart';

class ClientsScreen extends StatefulWidget {
  const ClientsScreen({super.key});

  @override
  State<ClientsScreen> createState() => _ClientsScreenState();
}

class _ClientsScreenState extends State<ClientsScreen> {
  final PlatformBridge _bridge = PlatformBridge();
  Timer? _timer;
  bool _loading = true;

  /// IP of the single expanded device card (null when all cards are collapsed).
  String? _expandedIp;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _refresh());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    final list = await _bridge.getClients();
    if (!mounted) return;
    final store = context.read<ClientStore>();
    await store.update(list);
    setState(() => _loading = false);
  }

  void _expand(String ip) {
    setState(() => _expandedIp = (_expandedIp == ip) ? _expandedIp : ip);
  }

  void _collapse() {
    if (_expandedIp == null) return;
    setState(() => _expandedIp = null);
  }

  Future<void> _removeClient(ConnectedClient client) async {
    final confirmed = await _confirmRemove(client);
    if (confirmed != true || !mounted) return;

    final store = context.read<ClientStore>();
    final wasRemembered = store.clients.any((c) => c.ip == client.ip);
    final removedFromTracker = await _bridge.removeClient(client.ip);
    await store.forget(client.ip);
    final goneFromStore = !store.clients.any((c) => c.ip == client.ip);

    if (!mounted) return;
    if (_expandedIp == client.ip) {
      setState(() => _expandedIp = null);
    }
    if (removedFromTracker || (wasRemembered && goneFromStore)) {
      _toast('Removed ${client.label}');
    } else {
      _toast('Could not remove ${client.label}.');
    }
  }

  Future<bool?> _confirmRemove(ConnectedClient client) {
    return showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove this device?'),
        content: Text(
          '${client.label}\n${client.ip}\n\n'
          'It will be forgotten by this server until it connects again.',
          key: const Key('remove_device_dialog'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Remove'),
          ),
        ],
      ),
    );
  }

  void _toast(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final server = context.watch<ServerService>();
    final status = server.status;
    final store = context.watch<ClientStore>();
    final theme = Theme.of(context);
    final clients = store.clients;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Connected Clients',
            style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: LayoutBuilder(builder: (context, constraints) {
        final maxW = constraints.maxWidth > 700 ? 680.0 : constraints.maxWidth;
        return Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxW),
            child: RefreshIndicator(
              onRefresh: _refresh,
              child: GestureDetector(
                behavior: HitTestBehavior.translucent,
                onTap: _collapse,
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    _buildMetricsCard(server),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Devices on your network',
                            style: theme.textTheme.titleMedium
                                ?.copyWith(fontWeight: FontWeight.bold),
                          ),
                        ),
                        if (!_loading)
                          Text(
                            '${clients.length} device${clients.length == 1 ? '' : 's'}',
                            style: theme.textTheme.bodySmall
                                ?.copyWith(color: Colors.grey),
                          ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    if (clients.isNotEmpty)
                      const Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          'Long-press a device to manage it.',
                          style: TextStyle(fontSize: 11, color: Colors.grey),
                        ),
                      ),
                    const SizedBox(height: 8),
                    _buildDeviceList(status.state.isStopped, clients),
                    if (status.state.isStopped && clients.isNotEmpty)
                      const Padding(
                        padding: EdgeInsets.only(top: 8),
                        child: Text(
                          'Server is offline — showing remembered devices.',
                          style: TextStyle(fontSize: 12, color: Colors.grey),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        );
      }),
    );
  }

  Widget _buildMetricsCard(ServerService server) {
    final status = server.status;
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Real-Time Streaming Metrics',
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildMetricColumn(
                    'Active Clients',
                    '${status.activeClients}',
                    Icons.devices,
                    Colors.greenAccent,
                  ),
                ),
                _buildDivider(),
                Expanded(
                  child: _buildMetricColumn(
                    'Active Streams',
                    '${status.activeStreams}',
                    Icons.play_circle_fill,
                    Colors.purpleAccent,
                  ),
                ),
                _buildDivider(),
                Expanded(
                  child: _buildMetricColumn(
                    'Data Served',
                    server.formattedBytesTransferred,
                    Icons.data_usage,
                    Colors.orangeAccent,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDeviceList(bool isServerStopped, List<ConnectedClient> clients) {
    if (isServerStopped && clients.isEmpty) {
      return _buildEmptyCard(
        Icons.dns_outlined,
        'Server offline',
        'Start the server to see which devices are connected and playing. '
            'Devices that connect will be remembered here.',
      );
    }
    if (_loading && clients.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 32),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (clients.isEmpty) {
      return _buildEmptyCard(
        Icons.devices_other,
        'No devices yet',
        'Open the web player on a computer, TV, or phone on the same Wi-Fi or '
            'hotspot. VLC appears with its computer name; browsers appear with '
            'their device name. Devices are remembered after their first visit.',
      );
    }
    return Column(
      children: [
        for (final client in clients) ...[
          _ClientCard(
            client: client,
            expanded: _expandedIp == client.ip,
            onTap: _collapse,
            onLongPress: () => _expand(client.ip),
            onRemove: () => _removeClient(client),
          ),
          const SizedBox(height: 12),
        ],
      ],
    );
  }

  Widget _buildEmptyCard(IconData icon, String title, String message) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Icon(icon, size: 40, color: Colors.grey),
            const SizedBox(height: 12),
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                  fontSize: 13, color: Colors.white70, height: 1.4),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricColumn(
      String label, String value, IconData icon, Color color) {
    return Column(
      children: [
        Icon(icon, color: color, size: 24),
        const SizedBox(height: 8),
        FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            value,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
        ),
        const SizedBox(height: 4),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }

  Widget _buildDivider() {
    return Container(
      width: 1,
      height: 40,
      color: Colors.grey.withValues(alpha: 0.3),
    );
  }
}

class _ClientCard extends StatelessWidget {
  final ConnectedClient client;
  final bool expanded;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final VoidCallback onRemove;

  const _ClientCard({
    required this.client,
    required this.expanded,
    required this.onTap,
    required this.onLongPress,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final icon = switch (client.kind) {
      'vlc' => Icons.computer,
      'browser' => Icons.language,
      'app' => Icons.smartphone,
      _ => Icons.smart_toy_outlined,
    };

    return AnimatedSize(
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeInOut,
      alignment: Alignment.topCenter,
      child: Card(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(
            color: expanded
                ? colorScheme.primary.withValues(alpha: 0.7)
                : colorScheme.outlineVariant.withValues(alpha: 0.5),
            width: expanded ? 1.5 : 1,
          ),
        ),
        color: expanded
            ? colorScheme.surfaceContainerHighest
            : colorScheme.surface,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          onLongPress: onLongPress,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeaderRow(theme, colorScheme, icon),
                if (client.playing.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  const Divider(height: 1),
                  const SizedBox(height: 8),
                  for (final playback in client.playing)
                    _buildPlaybackRow(playback),
                ],
                AnimatedSize(
                  duration: const Duration(milliseconds: 280),
                  curve: Curves.easeInOut,
                  alignment: Alignment.topCenter,
                  child: expanded
                      ? _buildRemoveSection(colorScheme)
                      : const SizedBox(width: double.infinity),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeaderRow(
      ThemeData theme, ColorScheme colorScheme, IconData icon) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          radius: 20,
          backgroundColor: colorScheme.surfaceContainerHighest,
          child: Icon(icon, size: 20, color: colorScheme.primary),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                client.label,
                style:
                    const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 2),
              Text(
                client.subtitle,
                style: const TextStyle(fontSize: 12, color: Colors.white70),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.circle,
              size: 10,
              color: client.online ? Colors.greenAccent : Colors.grey,
            ),
            const SizedBox(width: 4),
            Text(
              client.online ? 'Online' : 'Away',
              style: const TextStyle(fontSize: 11, color: Colors.grey),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildPlaybackRow(ConnectedPlayback playback) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        children: [
          Icon(
            playback.active ? Icons.play_arrow : Icons.pause,
            size: 16,
            color: playback.active ? Colors.greenAccent : Colors.grey,
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              playback.name,
              style: const TextStyle(fontSize: 13),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            playback.active ? 'Playing' : 'Paused',
            style: TextStyle(
              fontSize: 11,
              color: playback.active ? Colors.greenAccent : Colors.grey,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            _formatBytes(playback.bytes),
            style: const TextStyle(fontSize: 11, color: Colors.white54),
          ),
        ],
      ),
    );
  }

  Widget _buildRemoveSection(ColorScheme colorScheme) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeInOut,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, 6 * (1 - value)),
          child: child,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Divider(height: 1),
          const SizedBox(height: 12),
          SizedBox(
            height: 44,
            child: LiquidGlassButton(
              icon: Icons.delete_outline,
              label: 'Remove Device',
              onPressed: onRemove,
              borderRadius: 12,
              glowColor: colorScheme.error,
              height: 44,
            ),
          ),
        ],
      ),
    );
  }

  String _formatBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
