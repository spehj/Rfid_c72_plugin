package com.example.rfid_c72_plugin;

public class LocationData {
    private final int value;
    private final boolean valid;

    public LocationData(int value, boolean valid) {
        this.value = value;
        this.valid = valid;
    }

    public int getValue() {
        return value;
    }

    public boolean isValid() {
        return valid;
    }
}
