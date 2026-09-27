import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/connected_client.dart';
import '../../services/platform_bridge.dart';
import '../../services/server_service.dart';

class ClientsScreen extends StatefulWidget {
  const ClientsScreen({super.key});

  @override
  State<ClientsScreen> createState() => _ClientsScreenState();
}

class _ClientsScreenState extends State<ClientsScreen> {
  final PlatformBridge _bridge = PlatformBridge();
  Timer? _timer;
  List<ConnectedClient> _clients = const [];
  bool _loading = true;

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
    setState(() {
      _clients = list;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final server = context.watch<ServerService>();
    final status = server.status;
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Connected Clients', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _buildMetricsCard(server),
            const SizedBox(height: 16),
            Row(
              children: [
                Text(
                  'Devices on your network',
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                if (!_loading)
                  Text(
                    '${_clients.length} device${_clients.length == 1 ? '' : 's'}',
                    style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            _buildDeviceList(status.state.isStopped),
          ],
        ),
      ),
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
              style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildMetricColumn(
                  'Active Clients',
                  '${status.activeClients}',
                  Icons.devices,
                  Colors.greenAccent,
                ),
                _buildDivider(),
                _buildMetricColumn(
                  'Active Streams',
                  '${status.activeStreams}',
                  Icons.play_circle_fill,
                  Colors.purpleAccent,
                ),
                _buildDivider(),
                _buildMetricColumn(
                  'Data Served',
                  server.formattedBytesTransferred,
                  Icons.data_usage,
                  Colors.orangeAccent,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDeviceList(bool isServerStopped) {
    if (isServerStopped) {
      return _buildEmptyCard(
        Icons.dns_outlined,
        'Server offline',
        'Start the server to see which devices are connected and playing.',
      );
    }
    if (_loading && _clients.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 32),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_clients.isEmpty) {
      return _buildEmptyCard(
        Icons.devices_other,
        'No devices yet',
        'Open the web player on a computer, TV, or phone on the same Wi-Fi or hotspot. '
        'VLC appears with its computer name; browsers appear with their device name.',
      );
    }
    return Column(
      children: [
        for (final client in _clients) ...[
          _ClientCard(client: client),
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
              style: const TextStyle(fontSize: 13, color: Colors.white70, height: 1.4),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricColumn(String label, String value, IconData icon, Color color) {
    return Column(
      children: [
        Icon(icon, color: color, size: 24),
        const SizedBox(height: 8),
        Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
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
  const _ClientCard({required this.client});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final icon = switch (client.kind) {
      'vlc' => Icons.computer,
      'browser' => Icons.language,
      'app' => Icons.smartphone,
      _ => Icons.smart_toy_outlined,
    };

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CircleAvatar(
                  radius: 20,
                  backgroundColor: theme.colorScheme.surfaceContainerHighest,
                  child: Icon(icon, size: 20, color: theme.colorScheme.primary),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        client.label,
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
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
            ),
            if (client.playing.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Divider(height: 1),
              const SizedBox(height: 8),
              for (final playback in client.playing)
                Padding(
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
                ),
            ],
          ],
        ),
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