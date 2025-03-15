import 'dart:async';

import 'package:flutter/services.dart';
import 'package:rfid_c72_plugin/location_data.dart';

class RfidC72Plugin {
  static const MethodChannel _channel = MethodChannel('rfid_c72_plugin');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static const EventChannel connectedStatusSubjectEventChannel = EventChannel('connectedStatusSubject');
  // Used in flutter:
  // RfidC72Plugin.tagsStatusSubjectEventChannel.receiveBroadcastStream().listen(updateTags);
  static const EventChannel tagsStatusSubjectEventChannel = EventChannel('tagsStatusSubject');
  static const EventChannel barcodeScanSubjectEventChannel = EventChannel('barcodeScanSubject');
  static const EventChannel locationChannel = EventChannel('locationValueSubject');

  static Future<bool?> get isContinuousRfidReadActive async {
    return _channel.invokeMethod('isContinuousRfidReadActive');
  }

  static Future<bool?> get startRfidSingle async {
    return _channel.invokeMethod('startRfidSingle');
  }

  static Future<bool?> get startRfidContinuous async {
    return _channel.invokeMethod('startRfidContinuous');
  }

  static Future<bool?> get startBarcodeContinuous async {
    return _channel.invokeMethod('startBarcodeContinuous');
  }

  static Future<bool?> get stopRfid async {
    return _channel.invokeMethod('stopRfid');
  }

  static Future<bool?> get closeRfid async {
    return _channel.invokeMethod('closeRfid');
  }

  static Future<bool?> get clearData async {
    return _channel.invokeMethod('clearData');
  }

  static Future<bool?> get isEmptyTags async {
    return _channel.invokeMethod('isEmptyTags');
  }

  static Future<bool?> get connectRfid async {
    return _channel.invokeMethod('connectRfid');
  }

  static Future<bool?> get isRfidConnected async {
    return _channel.invokeMethod('isRfidConnected');
  }

  static Future<bool?> get connectBarcode async {
    return _channel.invokeMethod('connectBarcode');
  }

  static Future<bool?> get scanBarcode async {
    return _channel.invokeMethod('scanBarcode');
  }

  static Future<bool?> get stopScanBarcode async {
    return _channel.invokeMethod('stopScanBarcode');
  }

  static Future<bool?> get closeScan async {
    return _channel.invokeMethod('closeScan');
  }

  static Future<bool?> setPowerLevel(String value) async {
    return _channel.invokeMethod('setPowerLevel', <String, String>{'value': value});
  }

  static Future<bool?> setWorkArea(String value) async {
    return _channel.invokeMethod('setWorkArea', <String, String>{'value': value});
  }

  static Future<String?> get readBarcode async {
    final String? barcode = await _channel.invokeMethod('readBarcode');
    return barcode;
  }

  // Start tag location
  static Future<bool> startTagLocation(String epc) async {
    final result = await _channel.invokeMethod('startTagLocation', {'epc': epc});
    return result ?? false;
  }

  // Stop tag location
  static Future<bool> stopTagLocation() async {
    final result = await _channel.invokeMethod('stopTagLocation');
    return result ?? false;
  }

  // Check if location is running
  static Future<bool> isLocationRunning() async {
    final result = await _channel.invokeMethod('isLocationRunning');
    return result ?? false;
  }

  // Set location dynamic distance
  static Future<bool> setLocationDynamicDistance(int value) async {
    final result = await _channel.invokeMethod('setLocationDynamicDistance', {'value': value});
    return result ?? false;
  }

  static Stream<LocationData> get locationValues {
    return locationChannel.receiveBroadcastStream().map<LocationData>((value) {
      return LocationData.fromJson(value);
    });
  }
}
