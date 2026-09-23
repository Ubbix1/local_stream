import 'package:flutter_test/flutter_test.dart';
import 'package:localstream_mobile/main.dart';

void main() {
  testWidgets('LocalStreamApp renders main navigation', (WidgetTester tester) async {
    await tester.pumpWidget(const LocalStreamApp());

    // Expect navigation destinations
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Library'), findsOneWidget);
    expect(find.text('Server'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
  });
}
