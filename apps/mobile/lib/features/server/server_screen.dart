import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/server_service.dart';

class ServerScreen extends StatefulWidget {
  const ServerScreen({super.key});

  @override
  State<ServerScreen> createState() => _ServerScreenState();
}

class _ServerScreenState extends State<ServerScreen> {
  late TextEditingController _portController;

  @override
  void initState() {
    super.initState();
    final server = context.read<ServerService>();
    _portController = TextEditingController(text: '${server.configuredPort}');
  }

  @override
  void dispose() {
    _portController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final server = context.watch<ServerService>();
    final status = server.status;
    final isRunning = status.state.isRunning;
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Server Configuration', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Port Configuration Card
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.settings_input_antenna, color: colorScheme.primary, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'HTTP Port Settings',
                        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    isRunning
                        ? 'Server is running on port ${status.port}. Stop server to modify port.'
                        : 'Configure the listening port (default: 8080).',
                    style: const TextStyle(fontSize: 13),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _portController,
                          enabled: !isRunning,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: 'Port (1024 - 65535)',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      FilledButton(
                        onPressed: isRunning
                            ? null
                            : () {
                                final port = int.tryParse(_portController.text);
                                if (port != null && port >= 1024 && port <= 65535) {
                                  server.setConfiguredPort(port);
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Port set to $port')),
                                  );
                                } else {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(content: Text('Please enter a valid port between 1024 and 65535')),
                                  );
                                }
                              },
                        child: const Text('Save'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // mDNS / Discovery Card
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.radar, color: colorScheme.secondary, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'mDNS / NSD Discovery',
                        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  _buildDetailRow('Service Name', 'LocalStream'),
                  _buildDetailRow('Service Type', '_http._tcp. (DNS-SD)'),
                  _buildDetailRow('Status', isRunning ? 'Advertising on LAN' : 'Inactive'),
                  _buildDetailRow('Fallback', 'Direct IP connection is always supported'),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // API Endpoints Reference Card
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.api, color: colorScheme.tertiary, size: 20),
                      const SizedBox(width: 8),
                      Text(
                        'HTTP Server Endpoints',
                        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  _buildApiRow('GET', '/', 'Web media library landing page'),
                  _buildApiRow('GET', '/watch/:id', 'Web HTML5 video/audio player'),
                  _buildApiRow('GET', '/api/v1/info', 'Server metadata & protocol version'),
                  _buildApiRow('GET', '/api/v1/status', 'Server metrics & clients count'),
                  _buildApiRow('GET', '/api/v1/files', 'List all media files (JSON)'),
                  _buildApiRow('GET', '/api/v1/files/:id', 'Get metadata for specific item'),
                  _buildApiRow('GET', '/api/v1/stream/:id', 'HTTP Range streaming stream'),
                  _buildApiRow('HEAD', '/api/v1/stream/:id', 'Probe stream headers & length'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 13, color: Colors.grey)),
          Text(value, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }

  Widget _buildApiRow(String method, String path, String desc) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: method == 'GET' ? Colors.blue.withOpacity(0.2) : Colors.purple.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  method,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: method == 'GET' ? Colors.blueAccent : Colors.purpleAccent,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Text(
                path,
                style: const TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.bold, fontSize: 13),
              ),
            ],
          ),
          const SizedBox(height: 2),
          Text(desc, style: const TextStyle(fontSize: 12, color: Colors.grey)),
        ],
      ),
    );
  }
}
