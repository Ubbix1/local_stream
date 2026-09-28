enum ErrorCode {
  invalidInput,
  unavailable,
  permissionDenied,
  network,
  unsupported,
  internal
}

class AppError implements Exception {
  final ErrorCode code;
  final String userMessage;
  final Object? cause;
  const AppError(this.code, this.userMessage, {this.cause});
}
