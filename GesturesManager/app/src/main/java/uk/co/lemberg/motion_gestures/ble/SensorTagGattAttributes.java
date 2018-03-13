package uk.co.lemberg.motion_gestures.ble;

/**
 * Created by Tim on 12/03/2018.
 */

import java.util.HashMap;
import java.util.UUID;

public class SensorTagGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    /*
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }
    */

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        attributes.put("f000aa10-0451-4000-b000-000000000000","Accelerometer_Service");
        attributes.put("f000aa50-0451-4000-b000-000000000000","Gyroscope_Service");

        attributes.put("f000aa11-0451-4000-b000-000000000000","Accelerometer Data");
        attributes.put("f000aa51-0451-4000-b000-000000000000","Gyroscope Data");
    }


    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}