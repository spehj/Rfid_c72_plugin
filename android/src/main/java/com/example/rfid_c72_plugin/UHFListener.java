package com.example.rfid_c72_plugin;

public abstract class UHFListener {
    abstract void onRfidRead(String tagsJson);

    abstract void onBarcodeRead(String barcodeScan);

    abstract void onRfidConnect(boolean isRfidConnected, int powerLevel);
}
