import 'package:flutter/material.dart';
void main() => runApp(const LocalStreamApp());
class LocalStreamApp extends StatelessWidget {
  const LocalStreamApp({super.key});
  @override Widget build(BuildContext context) => MaterialApp(
    title: 'LocalStream', debugShowCheckedModeBanner: false,
    home: const Scaffold(body: Center(child: Text('LocalStream'))));
}
