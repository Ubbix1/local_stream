import 'package:flutter/services.dart';
class NativeBridge {
  static const channel = MethodChannel('localstream/mobile');
  Future<void> startServer() => channel.invokeMethod('startServer');
  Future<void> stopServer() => channel.invokeMethod('stopServer');
  Future<List<dynamic>> sharedMedia() async => (await channel.invokeMethod<List<dynamic>>('getSharedMedia')) ?? const [];
  Future<Map<dynamic, dynamic>> getPinState() async =>
      (await channel.invokeMethod<Map<dynamic, dynamic>>('getPinState')) ?? const {};
  Future<bool> setAccessPin(String pin) async =>
      await channel.invokeMethod('setAccessPin', {'pin': pin}) ?? false;
  Future<bool> clearAccessPin() async =>
      await channel.invokeMethod('clearAccessPin') ?? false;
}
