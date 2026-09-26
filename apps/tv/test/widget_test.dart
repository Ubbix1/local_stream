// The TV app boots into a shell with a server-connect surface. The library,
// folder browsing, and PIN flows all require a live server, so only the
// connection screen is exercised here.

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:localstream_tv/main.dart';

void main() {
  testWidgets('TV app renders the connect screen', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const LocalStreamApp());
    await tester.pumpAndSettle();

    expect(find.textContaining('Connect to a LocalStream server'), findsOneWidget);
    expect(find.textContaining('Server address'), findsOneWidget);
  });
}