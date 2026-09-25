import 'package:flutter/material.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings & Diagnostics', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Android 13-16 System & Permissions Card
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.android, color: Colors.greenAccent, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'Android Architecture (API 33 - 36)',
                        style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _buildSystemRow('Foreground Service', 'Persistent dataSync service with notification'),
                  _buildSystemRow('UI Independence', 'Server keeps streaming when Flutter UI is closed'),
                  _buildSystemRow('Storage Access', 'Storage Access Framework (SAF) + Content URIs'),
                  _buildSystemRow('Android Share', 'Generic ACTION_SEND / ACTION_SEND_MULTIPLE receiver'),
                  _buildSystemRow('Streaming Engine', 'Pure Kotlin ServerSocket + HTTP Range (206)'),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // About LocalStream
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.info_outline, color: colorScheme.primary, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'About LocalStream',
                        style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
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
                  const Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('App Version', style: TextStyle(fontSize: 12, color: Colors.grey)),
                      Text('0.1.0', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  const Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('Protocol Version', style: TextStyle(fontSize: 12, color: Colors.grey)),
                      Text('v1 (HTTP /api/v1)', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
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
          const Icon(Icons.check_circle_outline, size: 14, color: Colors.greenAccent),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                Text(desc, style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
