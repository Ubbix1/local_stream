import 'package:flutter/services.dart';
class NativeBridge {
  static const channel = MethodChannel('localstream/mobile');
  Future<void> startServer() => channel.invokeMethod('startServer');
  Future<void> stopServer() => channel.invokeMethod('stopServer');
  Future<List<dynamic>> sharedMedia() async => (await channel.invokeMethod<List<dynamic>>('getSharedMedia')) ?? const [];
}
