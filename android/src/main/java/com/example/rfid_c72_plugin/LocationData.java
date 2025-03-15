package com.example.rfid_c72_plugin;

import java.util.HashMap;
import java.util.Map;

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

    public Map<String, Object> toMap() {
        Map<String, Object> json = new HashMap<>();
        json.put("value", value);
        json.put("valid", valid);
        return json;
    }
}
