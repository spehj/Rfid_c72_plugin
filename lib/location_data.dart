class LocationData {
  final int value;
  final bool valid;

  LocationData({required this.value, required this.valid});

  factory LocationData.fromJson(Map<String, dynamic> json) => LocationData(
        value: json["value"],
        valid: json["valid"],
      );

  Map<String, dynamic> toJson() => {
        "value": value,
        "valid": valid,
      };
}
