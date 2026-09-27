import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'features/home/home_screen.dart';
import 'features/library/library_screen.dart';
import 'features/server/server_screen.dart';
import 'features/clients/clients_screen.dart';
import 'features/settings/settings_screen.dart';
import 'services/media_service.dart';
import 'services/server_service.dart';
import 'services/update_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const LocalStreamApp());
}

class LocalStreamApp extends StatelessWidget {
  const LocalStreamApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ServerService()),
        ChangeNotifierProvider(create: (_) => MediaService()),
        ChangeNotifierProvider(create: (_) => UpdateService()..init()),
      ],
      child: MaterialApp(
        title: 'LocalStream',
        debugShowCheckedModeBanner: false,
        themeMode: ThemeMode.dark,
        darkTheme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          colorScheme: ColorScheme.dark(
            primary: const Color(0xFF58A6FF),
            onPrimary: const Color(0xFF0D1117),
            primaryContainer: const Color(0xFF1F6FEB).withValues(alpha: 0.3),
            onPrimaryContainer: const Color(0xFF58A6FF),
            secondary: const Color(0xFF238636),
            surface: const Color(0xFF161B22),
            onSurface: const Color(0xFFF0F6FC),
            surfaceContainerHighest: const Color(0xFF21262D),
            outlineVariant: const Color(0xFF30363D),
          ),
          scaffoldBackgroundColor: const Color(0xFF0D1117),
          cardTheme: CardThemeData(
            color: const Color(0xFF161B22),
            elevation: 0,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: Color(0xFF30363D), width: 1),
            ),
          ),
          appBarTheme: const AppBarTheme(
            backgroundColor: Color(0xFF0D1117),
            elevation: 0,
            centerTitle: false,
          ),
          navigationBarTheme: NavigationBarThemeData(
            backgroundColor: const Color(0xFF161B22),
            indicatorColor: const Color(0xFF1F6FEB).withValues(alpha: 0.3),
            labelTextStyle: WidgetStateProperty.all(
              const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
            ),
          ),
        ),
        home: const MainNavigationScreen(),
      ),
    );
  }
}

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    HomeScreen(),
    LibraryScreen(),
    ServerScreen(),
    ClientsScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          const _UpdateBanner(),
          Expanded(
            child: IndexedStack(
              index: _currentIndex,
              children: _screens,
            ),
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (idx) => setState(() => _currentIndex = idx),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.video_library_outlined),
            selectedIcon: Icon(Icons.video_library),
            label: 'Library',
          ),
          NavigationDestination(
            icon: Icon(Icons.dns_outlined),
            selectedIcon: Icon(Icons.dns),
            label: 'Server',
          ),
          NavigationDestination(
            icon: Icon(Icons.devices_outlined),
            selectedIcon: Icon(Icons.devices),
            label: 'Clients',
          ),
          NavigationDestination(
            icon: Icon(Icons.tune_outlined),
            selectedIcon: Icon(Icons.tune),
            label: 'Settings',
          ),
        ],
      ),
    );
  }
}

/// Slim banner shown above the nav screens while a newer signed build is
/// available. [Update] opens the matching APK on the GitHub release page.
class _UpdateBanner extends StatelessWidget {
  const _UpdateBanner();

  @override
  Widget build(BuildContext context) {
    final service = context.watch<UpdateService>();
    if (!service.updateAvailable) return const SizedBox.shrink();
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.primaryContainer,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          child: Row(
            children: [
              Icon(Icons.system_update_alt, size: 18, color: scheme.onPrimaryContainer),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Update available: ${service.latestVersion}',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: scheme.onPrimaryContainer,
                  ),
                ),
              ),
              TextButton(
                onPressed: () => _openUpdate(context, service),
                child: const Text('Update'),
              ),
              TextButton(
                onPressed: service.dismiss,
                child: const Text('Later'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _openUpdate(BuildContext context, UpdateService service) async {
    final url = service.latestAssetUrl.isNotEmpty
        ? service.latestAssetUrl
        : service.latestReleaseUrl;
    if (url.isEmpty) {
      _toast(context, 'No download link available yet.');
      return;
    }
    try {
      final launched = await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
      if (!launched) throw Exception('launch rejected');
    } catch (_) {
      await Clipboard.setData(ClipboardData(text: url));
      if (!context.mounted) return;
      _toast(context, 'Could not open browser - download link copied.');
    }
  }

  void _toast(BuildContext context, String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}
