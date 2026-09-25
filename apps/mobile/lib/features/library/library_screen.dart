import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../models/media_item.dart';
import '../../services/media_service.dart';
import '../../services/server_service.dart';
import '../../widgets/liquid_glass_button.dart';
import '../../widgets/liquid_glass_icon_button.dart';

class LibraryScreen extends StatelessWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final media = context.watch<MediaService>();
    final server = context.watch<ServerService>();
    final items = media.filteredItems;
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Media Library',
            style: TextStyle(fontWeight: FontWeight.bold)),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(60),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: SearchBar(
              hintText: 'Search media...',
              leading: const Icon(Icons.search, size: 20),
              trailing: [
                if (media.searchQuery.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.clear, size: 18),
                    onPressed: () => media.setSearchQuery(''),
                  ),
              ],
              onChanged: media.setSearchQuery,
              elevation: const MaterialStatePropertyAll(1),
              backgroundColor:
                  MaterialStatePropertyAll(colorScheme.surfaceVariant),
            ),
          ),
        ),
      ),
      body: Column(
        children: [
          // Filter Chips
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                _buildFilterChip(
                    context, media, 'All', 'all', Icons.all_inclusive),
                const SizedBox(width: 8),
                _buildFilterChip(
                    context, media, 'Videos', 'video', Icons.movie_outlined),
                const SizedBox(width: 8),
                _buildFilterChip(context, media, 'Audio', 'audio',
                    Icons.music_note_outlined),
                const SizedBox(width: 8),
                _buildFilterChip(
                    context, media, 'Images', 'image', Icons.image_outlined),
              ],
            ),
          ),

          // Media List
          Expanded(
            child: Column(
              children: [
                if (media.importingFileName != null)
                  _buildImportProgress(context, media),
                Expanded(
                  child: media.isLoading
                      ? const Center(child: CircularProgressIndicator())
                      : items.isEmpty
                          ? _buildEmptyState(context, media)
                          : RefreshIndicator(
                              onRefresh: media.loadMedia,
                              child: ListView.builder(
                                padding: const EdgeInsets.only(
                                    bottom: 80, left: 16, right: 16, top: 8),
                                itemCount: items.length,
                                itemBuilder: (context, index) {
                                  final item = items[index];
                                  return _buildMediaTile(
                                      context, item, server, media);
                                },
                              ),
                            ),
                ),
              ],
            ),
          ),
        ],
      ),
      floatingActionButton: LiquidGlassIconButton(
        icon: Icons.add,
        tooltip: 'Add Media',
        size: 56,
        onPressed: () => _pickMediaFiles(context, media),
      ),
    );
  }

  Widget _buildImportProgress(BuildContext context, MediaService media) {
    final progress = media.importProgress;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 10),
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Loading ${media.importingFileName}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
          ),
          const SizedBox(height: 6),
          LinearProgressIndicator(value: progress),
          const SizedBox(height: 4),
          Text(
            progress == null
                ? 'Preparing media...'
                : '${(progress * 100).round()}%',
            style: const TextStyle(fontSize: 11),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(
    BuildContext context,
    MediaService media,
    String label,
    String filterValue,
    IconData icon,
  ) {
    final isSelected = media.selectedFilter == filterValue;
    return FilterChip(
      selected: isSelected,
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16),
          const SizedBox(width: 6),
          Text(label),
        ],
      ),
      onSelected: (_) => media.setFilter(filterValue),
    );
  }

  Widget _buildEmptyState(BuildContext context, MediaService media) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.folder_open,
                size: 64, color: theme.colorScheme.primary.withOpacity(0.5)),
            const SizedBox(height: 16),
            Text(
              media.searchQuery.isNotEmpty
                  ? 'No results for "${media.searchQuery}"'
                  : 'No media items in library',
              style: theme.textTheme.titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              media.searchQuery.isNotEmpty
                  ? 'Try a different search term or clear the filter.'
                  : 'Tap "Add Media" below to choose files from Downloads, Documents, Camera, or any other folder.',
              style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.textTheme.bodySmall?.color?.withOpacity(0.7)),
              textAlign: TextAlign.center,
            ),
            if (media.searchQuery.isNotEmpty) ...[
              const SizedBox(height: 16),
              SizedBox(
                height: 40,
                width: 168,
                child: LiquidGlassButton(
                  label: 'Clear Search',
                  onPressed: () => media.setSearchQuery(''),
                  borderRadius: 10,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildMediaTile(
    BuildContext context,
    MediaItem item,
    ServerService server,
    MediaService media,
  ) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isRunning = server.status.state.isRunning;

    IconData icon;
    Color iconColor;
    if (item.isVideo) {
      icon = Icons.play_circle_fill;
      iconColor = Colors.greenAccent;
    } else if (item.isAudio) {
      icon = Icons.audio_file;
      iconColor = Colors.purpleAccent;
    } else if (item.isImage) {
      icon = Icons.image;
      iconColor = Colors.blueAccent;
    } else {
      icon = Icons.insert_drive_file;
      iconColor = Colors.grey;
    }

    final streamUrl = '${server.status.primaryUrl}/api/v1/stream/${item.id}';
    final watchUrl = '${server.status.primaryUrl}/watch/${item.id}';

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        leading: CircleAvatar(
          backgroundColor: iconColor.withOpacity(0.15),
          child: Icon(icon, color: iconColor),
        ),
        title: Text(
          item.name,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        ),
        subtitle: Row(
          children: [
            Text(item.formattedSize, style: const TextStyle(fontSize: 12)),
            const Text(' • ', style: TextStyle(fontSize: 12)),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(
                color: colorScheme.surfaceVariant,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                item.source.toUpperCase(),
                style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: colorScheme.onSurfaceVariant),
              ),
            ),
          ],
        ),
        trailing: PopupMenuButton<String>(
          icon: const Icon(Icons.more_vert, size: 20),
          onSelected: (action) {
            switch (action) {
              case 'copy_stream':
                Clipboard.setData(ClipboardData(text: streamUrl));
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Copied Stream URL: $streamUrl')),
                );
                break;
              case 'copy_watch':
                Clipboard.setData(ClipboardData(text: watchUrl));
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Copied Watch URL: $watchUrl')),
                );
                break;
              case 'remove':
                media.removeMedia(item.id);
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Removed ${item.name}')),
                );
                break;
            }
          },
          itemBuilder: (context) => [
            PopupMenuItem(
              value: 'copy_stream',
              enabled: isRunning,
              child: const Row(
                children: [
                  Icon(Icons.link, size: 18),
                  SizedBox(width: 8),
                  Text('Copy Stream URL (VLC/Player)'),
                ],
              ),
            ),
            PopupMenuItem(
              value: 'copy_watch',
              enabled: isRunning,
              child: const Row(
                children: [
                  Icon(Icons.open_in_browser, size: 18),
                  SizedBox(width: 8),
                  Text('Copy Web Player URL'),
                ],
              ),
            ),
            const PopupMenuDivider(),
            const PopupMenuItem(
              value: 'remove',
              child: Row(
                children: [
                  Icon(Icons.delete_outline, size: 18, color: Colors.redAccent),
                  SizedBox(width: 8),
                  Text('Remove from Library',
                      style: TextStyle(color: Colors.redAccent)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickMediaFiles(BuildContext context, MediaService media) async {
    final count = await media.pickMediaFiles();
    if (context.mounted && count > 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content:
                Text('Added $count media ${count == 1 ? 'file' : 'files'}')),
      );
    }
  }
}
