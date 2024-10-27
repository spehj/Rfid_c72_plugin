import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:rfid_c72_plugin_example/scanner_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  runApp(const MyApp());
  await requestPermissions();
}

Future<void> requestPermissions() async {
  // Request storage permissions
  Map<Permission, PermissionStatus> statuses = await [
    Permission.storage,
    Permission.manageExternalStorage,
  ].request();
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: ScannerScreen(),
    );
  }
}
