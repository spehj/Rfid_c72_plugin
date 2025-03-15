import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import 'rfid_location_screen.dart';
import 'scanner_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
    _requestPermissions();
  }

  Future<void> _requestPermissions() async {
    // Request foreground location permission.
    final fgStatus = await Permission.location.request();
    print("Foreground location permission status: $fgStatus");

    // Request background location permission if foreground is granted.
    if (fgStatus.isGranted) {
      final bgStatus = await Permission.locationAlways.request();
      print("Background location permission status: $bgStatus");
    }

    // Now request storage permissions.
    Map<Permission, PermissionStatus> storageStatuses = await [
      Permission.storage,
      Permission.manageExternalStorage,
    ].request();
    print("Storage permission statuses: $storageStatuses");
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: const ScannerScreen(),
      routes: {
        '/location': (context) => const RfidLocationScreen(),
      },
    );
  }
}
