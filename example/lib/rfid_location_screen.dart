import 'dart:async';
import 'package:flutter/material.dart';
import 'package:rfid_c72_plugin/location_data.dart';
import 'package:rfid_c72_plugin/rfid_c72_plugin.dart';

class RfidLocationScreen extends StatefulWidget {
  const RfidLocationScreen({Key? key}) : super(key: key);

  @override
  State<RfidLocationScreen> createState() => _RfidLocationScreenState();
}

class _RfidLocationScreenState extends State<RfidLocationScreen> {
  final TextEditingController _epcController = TextEditingController();
  StreamSubscription<LocationData>? _locationSubscription;
  LocationData? _locationData;
  bool _isScanning = false;
  String _statusMessage = 'Enter an EPC and press Start';
  bool _isRfidConnected = false;

  @override
  void initState() {
    super.initState();
    _connectRfid();
  }

  Future<void> _connectRfid() async {
    try {
      final bool? connected = await RfidC72Plugin.connectRfid;
      setState(() {
        _isRfidConnected = connected ?? false;
        _statusMessage = connected != null && connected ? 'RFID Reader connected' : 'Failed to connect RFID Reader';
      });
    } catch (e) {
      setState(() {
        _statusMessage = 'Error connecting RFID Reader: $e';
      });
    }
  }

  Future<void> _startLocationScan() async {
    if (_epcController.text.isEmpty) {
      _showSnackBar('Please enter an EPC value');
      return;
    }

    if (!_isRfidConnected) {
      _showSnackBar('RFID Reader not connected');
      return;
    }

    setState(() {
      _statusMessage = 'Starting location scan...';
    });

    try {
      // Set dynamic distance to mid-range (adjust as needed)
      await RfidC72Plugin.setLocationDynamicDistance(30);
      // set power level
      await RfidC72Plugin.setPowerLevel(30.toString());

      final bool success = await RfidC72Plugin.startTagLocation(_epcController.text);

      if (success) {
        setState(() {
          _isScanning = true;
          _statusMessage = 'Locating tag: ${_epcController.text}';
          _locationData = null;
        });

        // Start listening to location updates
        _locationSubscription = RfidC72Plugin.locationValues.listen((LocationData data) {
          print("Location data: $data");
          setState(() {
            _locationData = data;
          });
        }, onError: (error) {
          _showSnackBar('Error receiving location data: $error');
        });
      } else {
        setState(() {
          _statusMessage = 'Failed to start location scan';
        });
      }
    } catch (e) {
      setState(() {
        _statusMessage = 'Error: $e';
        _isScanning = false;
      });
    }
  }

  Future<void> _stopLocationScan() async {
    setState(() {
      _statusMessage = 'Stopping location scan...';
    });

    try {
      final bool success = await RfidC72Plugin.stopTagLocation();

      _locationSubscription?.cancel();
      _locationSubscription = null;

      setState(() {
        _isScanning = false;
        _statusMessage = success ? 'Location scan stopped' : 'Failed to stop location scan';
      });
    } catch (e) {
      setState(() {
        _statusMessage = 'Error stopping scan: $e';
        _isScanning = false;
      });
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  void dispose() {
    _locationSubscription?.cancel();
    _epcController.dispose();
    RfidC72Plugin.stopTagLocation();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('RFID Tag Locator'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Connection status indicator
            Container(
              padding: const EdgeInsets.all(8.0),
              decoration: BoxDecoration(
                color: _isRfidConnected ? Colors.green.shade100 : Colors.red.shade100,
                borderRadius: BorderRadius.circular(8.0),
              ),
              child: Row(
                children: [
                  Icon(
                    _isRfidConnected ? Icons.check_circle : Icons.error,
                    color: _isRfidConnected ? Colors.green : Colors.red,
                  ),
                  const SizedBox(width: 8.0),
                  Text(
                    _isRfidConnected ? 'RFID Reader Connected' : 'RFID Reader Disconnected',
                    style: TextStyle(
                      color: _isRfidConnected ? Colors.green : Colors.red,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const Spacer(),
                  if (!_isRfidConnected)
                    ElevatedButton(
                      onPressed: _connectRfid,
                      child: const Text('Connect'),
                    ),
                ],
              ),
            ),

            const SizedBox(height: 16.0),

            // EPC Input field
            TextField(
              controller: _epcController,
              decoration: const InputDecoration(
                labelText: 'EPC Value',
                hintText: 'Enter the EPC to locate',
                border: OutlineInputBorder(),
              ),
              enabled: !_isScanning && _isRfidConnected,
            ),

            const SizedBox(height: 16.0),

            // Status message
            Text(
              _statusMessage,
              style: const TextStyle(fontStyle: FontStyle.italic),
              textAlign: TextAlign.center,
            ),

            const SizedBox(height: 24.0),

            // Location indicator
            Expanded(
              child: Center(
                child: _isScanning ? _buildLocationIndicator() : _buildIdleIndicator(),
              ),
            ),

            const SizedBox(height: 24.0),

            // Action buttons
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isScanning || !_isRfidConnected ? null : _startLocationScan,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green,
                      padding: const EdgeInsets.symmetric(vertical: 12.0),
                    ),
                    child: const Text('Start Location'),
                  ),
                ),
                const SizedBox(width: 16.0),
                Expanded(
                  child: ElevatedButton(
                    onPressed: !_isScanning ? null : _stopLocationScan,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.red,
                      padding: const EdgeInsets.symmetric(vertical: 12.0),
                    ),
                    child: const Text('Stop Location'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIdleIndicator() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(
          Icons.location_searching,
          size: 80.0,
          color: Colors.grey.shade400,
        ),
        const SizedBox(height: 16.0),
        const Text(
          'Not scanning',
          style: TextStyle(fontSize: 18.0, color: Colors.grey),
        ),
      ],
    );
  }

  Widget _buildLocationIndicator() {
    final value = _locationData?.value ?? 0;
    final isValid = _locationData?.valid ?? false;

    // Calculate a color based on signal strength (0-100)
    final Color signalColor =
        isValid ? Color.lerp(Colors.blue.shade100, Colors.blue.shade900, value / 100) ?? Colors.blue : Colors.grey;

    // Calculate size based on signal strength
    final double size = 100 + (value / 2);

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Animated signal strength indicator
        AnimatedContainer(
          duration: const Duration(milliseconds: 300),
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: signalColor.withOpacity(0.3),
            shape: BoxShape.circle,
            border: Border.all(color: signalColor, width: 2.0),
          ),
          child: Center(
            child: Text(
              isValid ? '$value%' : 'Invalid',
              style: TextStyle(
                fontSize: 24.0,
                fontWeight: FontWeight.bold,
                color: signalColor,
              ),
            ),
          ),
        ),

        const SizedBox(height: 24.0),

        // Signal strength text
        Text(
          isValid ? _getSignalStrengthDescription(value) : 'Searching for tag...',
          style: TextStyle(
            fontSize: 18.0,
            fontWeight: FontWeight.bold,
            color: isValid ? signalColor : Colors.grey,
          ),
        ),

        if (isValid) ...[
          const SizedBox(height: 8.0),
          Text(
            'The higher the value, the closer you are',
            style: TextStyle(
              fontSize: 14.0,
              color: Colors.grey.shade600,
            ),
          ),
        ],
      ],
    );
  }

  String _getSignalStrengthDescription(int value) {
    if (value >= 90) return 'Very close!';
    if (value >= 70) return 'Getting closer!';
    if (value >= 50) return 'On the right track';
    if (value >= 30) return 'Signal detected';
    return 'Signal is weak';
  }
}
