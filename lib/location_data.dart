class LocationData {
  final int value;
  final bool valid;

  LocationData({required this.value, required this.valid});

  factory LocationData.fromJson(Map<Object?, Object?> json) => LocationData(
        value: json["value"] as int,
        valid: json["valid"] as bool,
      );

  Map<String, dynamic> toJson() => {
        "value": value,
        "valid": valid,
      };
}
