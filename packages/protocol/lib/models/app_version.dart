class AppVersion {
  const AppVersion({this.versionName = '', this.versionCode = 0});

  final String versionName;
  final int versionCode;

  factory AppVersion.fromMap(Map<dynamic, dynamic> map) {
    return AppVersion(
      versionName: (map['versionName'] as String?) ?? '',
      versionCode: (map['versionCode'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toMap() => {
        'versionName': versionName,
        'versionCode': versionCode,
      };
}
