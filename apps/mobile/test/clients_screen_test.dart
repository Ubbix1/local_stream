import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:localstream_mobile/features/clients/clients_screen.dart';
import 'package:localstream_mobile/models/connected_client.dart';
import 'package:localstream_mobile/services/client_store.dart';
import 'package:localstream_mobile/services/server_service.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Mocks the method + event channels so [ServerService] can initialize without
/// a real platform layer. The event subscription only needs to exist; no
/// events are emitted.
void _mockPlatform() {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    const MethodChannel('localstream/mobile'),
    (call) async => null,
  );
  messenger.setMockStreamHandler(
    const EventChannel('localstream/mobile/events'),
    MockStreamHandler.inline(
        onListen: (arguments, events) {}, onCancel: (_) {}),
  );
}

ConnectedClient _device(String ip, String label, {String kind = 'browser'}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return ConnectedClient(
    ip: ip,
    label: label,
    kind: kind,
    browser: kind == 'vlc' ? null : 'Chrome',
    browserVersion: '120',
    platform: 'Windows',
    device: null,
    hostname: 'pc.local',
    online: true,
    firstSeenMs: now,
    lastSeenMs: now,
    bytes: 0,
    playing: const [],
  );
}

Future<ClientStore> _pumpClients(
  WidgetTester tester,
  List<ConnectedClient> devices,
) async {
  SharedPreferences.setMockInitialValues({});
  final store = ClientStore();
  addTearDown(store.dispose);
  await store.init();
  await store.update(devices);
  await tester.pumpWidget(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ServerService()),
        ChangeNotifierProvider<ClientStore>.value(value: store),
      ],
      child: const MaterialApp(home: ClientsScreen()),
    ),
  );
  await tester.pump();
  return store;
}

Future<void> _teardown(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 4));
  await tester.pumpWidget(const SizedBox());
  await tester.pump();
}

Finder _cardOf(String label) =>
    find.ancestor(of: find.text(label), matching: find.byType(Card)).first;

void main() {
  setUp(_mockPlatform);

  testWidgets('device cards render collapsed without any Remove action',
      (tester) async {
    await _pumpClients(tester, [_device('192.168.1.10', 'Chrome')]);

    expect(find.text('Chrome'), findsOneWidget);
    expect(find.text('Remove Device'), findsNothing);

    await _teardown(tester);
  });

  testWidgets('long-press expands the card and reveals Remove Device inside it',
      (tester) async {
    await _pumpClients(tester, [_device('192.168.1.10', 'Chrome')]);

    await tester.longPress(find.text('Chrome'));
    await tester.pumpAndSettle();

    expect(find.text('Remove Device'), findsOneWidget);
    expect(
      find.descendant(
          of: _cardOf('Chrome'), matching: find.text('Remove Device')),
      findsOneWidget,
    );

    await _teardown(tester);
  });

  testWidgets('only one card may be expanded at a time', (tester) async {
    await _pumpClients(tester, [
      _device('192.168.1.10', 'Chrome'),
      _device('192.168.1.11', 'VLC', kind: 'vlc'),
    ]);

    await tester.longPress(find.text('Chrome'));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
          of: _cardOf('Chrome'), matching: find.text('Remove Device')),
      findsOneWidget,
    );

    await tester.longPress(find.text('VLC'));
    await tester.pumpAndSettle();

    expect(find.text('Remove Device'), findsOneWidget);
    expect(
      find.descendant(of: _cardOf('VLC'), matching: find.text('Remove Device')),
      findsOneWidget,
    );
    expect(
      find.descendant(
          of: _cardOf('Chrome'), matching: find.text('Remove Device')),
      findsNothing,
    );

    await _teardown(tester);
  });

  testWidgets('tapping elsewhere or another device collapses the card',
      (tester) async {
    await _pumpClients(tester, [
      _device('192.168.1.10', 'Chrome'),
      _device('192.168.1.11', 'VLC', kind: 'vlc'),
    ]);

    await tester.longPress(find.text('Chrome'));
    await tester.pumpAndSettle();
    expect(find.text('Remove Device'), findsOneWidget);

    await tester.tap(find.text('VLC'));
    await tester.pumpAndSettle();
    expect(find.text('Remove Device'), findsNothing);

    await _teardown(tester);
  });

  testWidgets('Cancel keeps the device; Remove removes it after confirmation',
      (tester) async {
    await _pumpClients(tester, [_device('192.168.1.10', 'Chrome')]);

    await tester.longPress(find.text('Chrome'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Remove Device'));
    await tester.pumpAndSettle();
    expect(find.text('Remove this device?'), findsOneWidget);

    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.text('Remove this device?'), findsNothing);
    expect(find.text('Chrome'), findsOneWidget);

    await tester.tap(find.text('Remove Device'));
    await tester.pumpAndSettle();
    expect(find.text('Remove this device?'), findsOneWidget);

    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();

    expect(find.text('Chrome'), findsNothing);
    expect(find.text('Remove Device'), findsNothing);
    expect(find.text('Removed Chrome'), findsOneWidget);

    await _teardown(tester);
  });
}
