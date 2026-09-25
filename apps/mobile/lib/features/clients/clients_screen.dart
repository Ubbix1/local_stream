import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/server_service.dart';

class ClientsScreen extends StatelessWidget {
  const ClientsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final server = context.watch<ServerService>();
    final status = server.status;
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Connected Clients', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Real-time Bandwidth & Connections Overview
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Real-Time Streaming Metrics',
                    style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
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
          ),

          const SizedBox(height: 16),

          // Stream Concurrency Information
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.bolt, color: colorScheme.primary, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'Streaming Concurrency',
                        style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'LocalStream handles up to 32 concurrent streaming connections via Kotlin coroutines and bounded I/O buffers. Multiple devices (TV, browser, phone) can stream independently without blocking.',
                    style: TextStyle(fontSize: 13, height: 1.4),
                  ),
                  const SizedBox(height: 12),
                  _buildTipRow('Memory Safety', 'Files are streamed in 64KB chunks — even a 50GB 4K remux will not consume phone RAM.'),
                  const SizedBox(height: 8),
                  _buildTipRow('Range Requests', 'HTTP 206 Partial Content enables instant seeking anywhere in the video without downloading prior content.'),
                  const SizedBox(height: 8),
                  _buildTipRow('Failure Isolation', 'If a client disconnects or pauses, only that socket is closed — other clients remain completely unaffected.'),
                ],
              ),
            ),
          ),
        ],
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

  Widget _buildTipRow(String title, String desc) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Icon(Icons.check, size: 16, color: Colors.greenAccent),
        const SizedBox(width: 8),
        Expanded(
          child: RichText(
            text: TextSpan(
              style: const TextStyle(fontSize: 12, color: Colors.white70),
              children: [
                TextSpan(text: '$title: ', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
                TextSpan(text: desc),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
