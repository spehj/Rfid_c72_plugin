import 'package:flutter/material.dart';
import 'package:rfid_c72_plugin/rfid_c72_plugin.dart';
import 'package:rfid_c72_plugin/tag_epc.dart';

class ScannerScreen extends StatefulWidget {
  const ScannerScreen({Key? key}) : super(key: key);

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  final List<String> _scannedBarcodes = [];
  final Set<String> _scannedRfidTags = {};
  bool _isContinuousReading = false;

  @override
  void initState() {
    super.initState();
    _initializeScanners();
    // Listen for RFID scans
    RfidC72Plugin.tagsStatusSubjectEventChannel.receiveBroadcastStream().listen(_handleRfidScan);
    // Listen for barcode scans
    RfidC72Plugin.barcodeScanSubjectEventChannel.receiveBroadcastStream().listen(_handleBarcodeScan);
  }

  Future<void> _initializeScanners() async {
    // Connect to RFID reader
    await RfidC72Plugin.connectRfid;
    // Connect to Barcode scanner
    await RfidC72Plugin.connectBarcode;
  }

  void _handleRfidScan(dynamic event) {
    if (event != null) {
      try {
        // Parse the JSON string containing RFID data
        final List<TagEpc> tags = TagEpc.parseTags(event.toString());
        setState(() {
          for (var tag in tags) {
            final epc = tag.epc;
            _scannedRfidTags.add(epc); // Set automatically handles duplicates
          }
        });
      } catch (e) {
        debugPrint('Error parsing RFID data: $e');
      }
    }
  }

  void _handleBarcodeScan(dynamic event) {
    if (event != null) {
      setState(() {
        _scannedBarcodes.add(event.toString());
      });
    }
  }

  Future<void> _startSingleRfidRead() async {
    await RfidC72Plugin.startRfidSingle;
  }

  Future<void> _toggleContinuousRfidRead() async {
    setState(() {
      _isContinuousReading = !_isContinuousReading;
    });

    if (_isContinuousReading) {
      await RfidC72Plugin.startRfidContinuous;
    } else {
      await RfidC72Plugin.stopRfid;
    }
  }

  Future<void> _scanBarcode() async {
    await RfidC72Plugin.scanBarcode;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scanner Demo'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton(
                  onPressed: _startSingleRfidRead,
                  child: const Text('Single RFID Read'),
                ),
                ElevatedButton(
                  onPressed: _toggleContinuousRfidRead,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _isContinuousReading ? Colors.red : null,
                  ),
                  child: Text(_isContinuousReading ? 'Stop Reading' : 'Continuous Read'),
                ),
                ElevatedButton(
                  onPressed: _scanBarcode,
                  child: const Text('Scan Barcode'),
                ),
              ],
            ),
          ),
          Expanded(
            child: DefaultTabController(
              length: 2,
              child: Column(
                children: [
                  const TabBar(
                    labelColor: Colors.blue,
                    tabs: [
                      Tab(text: 'RFID Tags'),
                      Tab(text: 'Barcodes'),
                    ],
                  ),
                  Expanded(
                    child: TabBarView(
                      children: [
                        // RFID Tags List
                        ListView.builder(
                          itemCount: _scannedRfidTags.length,
                          itemBuilder: (context, index) {
                            return ListTile(
                              leading: const Icon(Icons.nfc),
                              title: Text(_scannedRfidTags.elementAt(index)),
                            );
                          },
                        ),
                        // Barcodes List
                        ListView.builder(
                          itemCount: _scannedBarcodes.length,
                          itemBuilder: (context, index) {
                            return ListTile(
                              leading: const Icon(Icons.qr_code),
                              title: Text(_scannedBarcodes[index]),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    RfidC72Plugin.stopRfid;
    RfidC72Plugin.stopScanBarcode;
    super.dispose();
  }
}
