import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:localstream_mobile/features/home/home_screen.dart';
import 'package:localstream_mobile/services/media_service.dart';
import 'package:localstream_mobile/services/server_service.dart';
import 'package:provider/provider.dart';

void main() {
  testWidgets('home screen has no "How to connect & stream" section',
      (tester) async {
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

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => ServerService()),
          ChangeNotifierProvider(create: (_) => MediaService()),
        ],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );
    await tester.pump();

    expect(find.text('How to connect & stream'), findsNothing);
    expect(find.textContaining('Open any browser'), findsNothing);

    // The useful server info the section must not have replaced.
    expect(find.text('Server Stopped'), findsOneWidget);
    expect(find.text('Media Items'), findsOneWidget);
    expect(find.text('Connected Clients'), findsOneWidget);
    expect(find.text('Active Streams'), findsOneWidget);
    expect(find.text('Data Served'), findsOneWidget);

    await tester.pumpWidget(const SizedBox());
    await tester.pump();
  });
}
