import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../models/server_status.dart';
import '../../services/media_service.dart';
import '../../services/server_service.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final server = context.watch<ServerService>();
    final media = context.watch<MediaService>();
    final status = server.status;
    final isRunning = status.state.isRunning;
    final isStarting = status.state.isStarting;

    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(Icons.stream, color: colorScheme.onPrimaryContainer, size: 20),
            ),
            const SizedBox(width: 10),
            const Text('LocalStream', style: TextStyle(fontWeight: FontWeight.bold)),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh Status',
            onPressed: () {
              server.refreshStatus();
              media.loadMedia();
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await server.refreshStatus();
          await media.loadMedia();
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Server Status Card
            _buildStatusCard(context, server, status),

            const SizedBox(height: 16),

            // Server URLs card (when running)
            if (isRunning) ...[
              _buildAddressesCard(context, status),
              const SizedBox(height: 16),
            ],

            // Quick Stats Grid
            _buildMetricsGrid(context, server, media, status),

            const SizedBox(height: 16),

            // Quick Help / Share Card
            _buildHowToConnectCard(context, isRunning),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusCard(BuildContext context, ServerService server, ServerStatus status) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isRunning = status.state.isRunning;
    final isStarting = status.state.isStarting;
    final isStopping = status.state.isStopping;

    Color statusColor;
    String statusText;
    IconData statusIcon;

    switch (status.state) {
      case ServerLifecycleState.running:
        statusColor = Colors.greenAccent;
        statusText = 'Server Running';
        statusIcon = Icons.check_circle;
        break;
      case ServerLifecycleState.starting:
        statusColor = Colors.amberAccent;
        statusText = 'Starting Server...';
        statusIcon = Icons.hourglass_top;
        break;
      case ServerLifecycleState.stopping:
        statusColor = Colors.orangeAccent;
        statusText = 'Stopping Server...';
        statusIcon = Icons.hourglass_bottom;
        break;
      case ServerLifecycleState.error:
        statusColor = Colors.redAccent;
        statusText = 'Server Error';
        statusIcon = Icons.error;
        break;
      case ServerLifecycleState.stopped:
      default:
        statusColor = Colors.grey;
        statusText = 'Server Stopped';
        statusIcon = Icons.stop_circle_outlined;
        break;
    }

    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      width: 12,
                      height: 12,
                      decoration: BoxDecoration(
                        color: statusColor,
                        shape: BoxShape.circle,
                        boxShadow: isRunning
                            ? [BoxShadow(color: statusColor.withOpacity(0.5), blurRadius: 8, spreadRadius: 2)]
                            : null,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      statusText,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: isRunning ? Colors.greenAccent : null,
                      ),
                    ),
                  ],
                ),
                if (isRunning)
                  Chip(
                    label: Text(
                      'Uptime: ${server.formattedUptime}',
                      style: const TextStyle(fontSize: 12),
                    ),
                    visualDensity: VisualDensity.compact,
                    backgroundColor: colorScheme.surfaceVariant,
                  ),
              ],
            ),
            if (status.errorMessage != null) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.red.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.red.withOpacity(0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline, color: Colors.redAccent, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        status.errorMessage!,
                        style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: FilledButton.icon(
                onPressed: (isStarting || isStopping)
                    ? null
                    : () {
                        if (isRunning) {
                          server.stopServer();
                        } else {
                          server.startServer();
                        }
                      },
                style: FilledButton.styleFrom(
                  backgroundColor: isRunning ? Colors.redAccent.shade700 : colorScheme.primary,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                icon: Icon(isRunning ? Icons.stop : Icons.play_arrow),
                label: Text(
                  isRunning ? 'Stop Server' : 'Start Server',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAddressesCard(BuildContext context, ServerStatus status) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final addresses = status.addresses;
    final port = status.port;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.wifi, size: 18, color: colorScheme.primary),
                const SizedBox(width: 8),
                Text(
                  'Local Network Addresses',
                  style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Devices on the same Wi-Fi or hotspot can open these in any browser or media player:',
              style: theme.textTheme.bodySmall?.copyWith(color: theme.textTheme.bodySmall?.color?.withOpacity(0.7)),
            ),
            const SizedBox(height: 12),
            if (addresses.isEmpty)
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: colorScheme.surfaceVariant,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text('Connecting to network... check Wi-Fi or Hotspot.'),
              )
            else
              ...addresses.map((ip) {
                final url = 'http://$ip:$port';
                return Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: colorScheme.surfaceVariant,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: colorScheme.outlineVariant.withOpacity(0.5)),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      SelectableText(
                        url,
                        style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w600),
                      ),
                      IconButton(
                        icon: const Icon(Icons.copy, size: 18),
                        tooltip: 'Copy URL',
                        onPressed: () {
                          Clipboard.setData(ClipboardData(text: url));
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('Copied $url to clipboard'),
                              duration: const Duration(seconds: 2),
                            ),
                          );
                        },
                      ),
                    ],
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricsGrid(
    BuildContext context,
    ServerService server,
    MediaService media,
    ServerStatus status,
  ) {
    return GridView.count(
      crossAxisCount: 2,
      crossAxisSpacing: 12,
      mainAxisSpacing: 12,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      childAspectRatio: 1.5,
      children: [
        _buildMetricTile(
          context,
          title: 'Media Items',
          value: '${media.items.length}',
          subtitle: 'Available to stream',
          icon: Icons.movie_outlined,
          color: Colors.blueAccent,
        ),
        _buildMetricTile(
          context,
          title: 'Connected Clients',
          value: '${status.activeClients}',
          subtitle: 'Active connections',
          icon: Icons.devices,
          color: Colors.greenAccent,
        ),
        _buildMetricTile(
          context,
          title: 'Active Streams',
          value: '${status.activeStreams}',
          subtitle: 'Range streaming',
          icon: Icons.play_circle_outline,
          color: Colors.purpleAccent,
        ),
        _buildMetricTile(
          context,
          title: 'Data Served',
          value: server.formattedBytesTransferred,
          subtitle: 'Total transferred',
          icon: Icons.data_usage,
          color: Colors.orangeAccent,
        ),
      ],
    );
  }

  Widget _buildMetricTile(
    BuildContext context, {
    required String title,
    required String value,
    required String subtitle,
    required IconData icon,
    required Color color,
  }) {
    final theme = Theme.of(context);
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(title, style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w500)),
                Icon(icon, size: 18, color: color),
              ],
            ),
            Text(
              value,
              style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
            ),
            Text(
              subtitle,
              style: theme.textTheme.labelSmall?.copyWith(color: theme.textTheme.bodySmall?.color?.withOpacity(0.6)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHowToConnectCard(BuildContext context, bool isRunning) {
    final theme = Theme.of(context);
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.info_outline, size: 18),
                const SizedBox(width: 8),
                Text(
                  'How to connect & stream',
                  style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 10),
            _buildStepRow('1', 'Connect your laptop, TV, or other device to the same Wi-Fi or Phone Hotspot.'),
            const SizedBox(height: 8),
            _buildStepRow('2', 'Open any browser and type the URL shown above.'),
            const SizedBox(height: 8),
            _buildStepRow('3', 'Or in VLC / media player: Open Network Stream and paste a file\'s stream link.'),
            const SizedBox(height: 8),
            _buildStepRow('4', 'Share any video/audio from Files, Telegram, or WhatsApp using Android Share.'),
          ],
        ),
      ),
    );
  }

  Widget _buildStepRow(String number, String text) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          radius: 10,
          child: Text(number, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold)),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(text, style: const TextStyle(fontSize: 13, height: 1.3)),
        ),
      ],
    );
  }
}
