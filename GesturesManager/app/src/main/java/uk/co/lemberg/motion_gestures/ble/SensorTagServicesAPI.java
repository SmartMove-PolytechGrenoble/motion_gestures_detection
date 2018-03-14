package uk.co.lemberg.motion_gestures.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.nio.ByteBuffer;
import java.util.UUID;

import static java.lang.Thread.sleep;

/**
 * Created by Tim on 12/03/2018.
 */

public class SensorTagServicesAPI {
    /**
     * This is a self-contained function for turning on the magnetometer
     * sensor. It must be called AFTER the onServicesDiscovered callback
     * is received.
     */
    static UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static UUID accelServiceUuid = UUID.fromString("f000aa10-0451-4000-b000-000000000000");
    public static UUID accelDataUuid = UUID.fromString("f000aa11-0451-4000-b000-000000000000");
    public static UUID accelConfigUuid = UUID.fromString("f000aa12-0451-4000-b000-000000000000");
    public static UUID accelPeriodUuid = UUID.fromString("f000aa13-0451-4000-b000-000000000000");

    public static UUID gyroServiceUuid = UUID.fromString("f000aa50-0451-4000-b000-000000000000");
    public static UUID gyroDataUuid = UUID.fromString("f000aa51-0451-4000-b000-000000000000");
    public static UUID gyroConfigUuid = UUID.fromString("f000aa52-0451-4000-b000-000000000000");
    public static UUID gyroPeriodUuid = UUID.fromString("f000aa53-0451-4000-b000-000000000000");

    public static void turnOnAccelerometer(BluetoothGatt bluetoothGatt) {

        BluetoothGattService accelService = bluetoothGatt.getService(accelServiceUuid);
        BluetoothGattCharacteristic config = accelService.getCharacteristic(accelConfigUuid);
        config.setValue(new byte[]{03}); //NB: the config value is different for the Gyroscope
        bluetoothGatt.writeCharacteristic(config);
    }

    public static void enableAccelerometerNotifications(BluetoothGatt bluetoothGatt) {

        BluetoothGattService accelService = bluetoothGatt.getService(accelServiceUuid);
        BluetoothGattCharacteristic accelDataCharacteristic = accelService.getCharacteristic(accelDataUuid);
        bluetoothGatt.setCharacteristicNotification(accelDataCharacteristic, true); //Enabled locally

        BluetoothGattDescriptor config = accelDataCharacteristic.getDescriptor(CCC);
        config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(config); //Enabled remotely
    }

    public static BluetoothGattCharacteristic getAccelerometerCharacteristic(BluetoothGatt bluetoothGatt){

        BluetoothGattService accelService = bluetoothGatt.getService(accelServiceUuid);
        BluetoothGattCharacteristic accelDataCharacteristic = accelService.getCharacteristic(accelDataUuid);
        return accelDataCharacteristic;
    }

    public static void setAccelerometerPeriod(BluetoothGatt bluetoothGatt, byte period){

        //Period =[Input*10]ms(lower limit100ms),default 1000ms
        //byte[] periodToSend = ByteBuffer.allocate(4).putInt(period/10).array();

        BluetoothGattService accelService = bluetoothGatt.getService(accelServiceUuid);
        BluetoothGattCharacteristic config = accelService.getCharacteristic(accelPeriodUuid);
        //config.setValue(periodToSend);
        config.setValue(new byte[]{period});
        bluetoothGatt.writeCharacteristic(config);
        bluetoothGatt.readCharacteristic(config);
    }


    public static void turnOnGyroscope(BluetoothGatt bluetoothGatt) throws InterruptedException {

        BluetoothGattService gyroService = bluetoothGatt.getService(gyroServiceUuid);
        BluetoothGattCharacteristic config = gyroService.getCharacteristic(gyroConfigUuid);
        config.setValue(new byte[]{7});
        bluetoothGatt.writeCharacteristic(config);

    }

    public static void enableGyroscopeNotifications(BluetoothGatt bluetoothGatt) {

        BluetoothGattService gyroService = bluetoothGatt.getService(gyroServiceUuid);
        BluetoothGattCharacteristic gyroDataCharacteristic = gyroService.getCharacteristic(gyroDataUuid);
        bluetoothGatt.setCharacteristicNotification(gyroDataCharacteristic, true); //Enabled locally

        BluetoothGattDescriptor config = gyroDataCharacteristic.getDescriptor(CCC);
        config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(config); //Enabled remotely
    }

    public static BluetoothGattCharacteristic getGyroscopeCharacteristic(BluetoothGatt bluetoothGatt){

        BluetoothGattService gyroService = bluetoothGatt.getService(gyroServiceUuid);
        BluetoothGattCharacteristic gyroDataCharacteristic = gyroService.getCharacteristic(gyroDataUuid);
        return gyroDataCharacteristic;
    }

    public static void setGyroscopePeriod(BluetoothGatt bluetoothGatt, byte period){

        //Period =[Input*10]ms(lower limit100ms),default 1000ms
        //byte[] periodToSend = ByteBuffer.allocate(4).putInt(period/10).array();

        BluetoothGattService gyroService = bluetoothGatt.getService(gyroServiceUuid);
        BluetoothGattCharacteristic config = gyroService.getCharacteristic(gyroPeriodUuid);
        config.setValue(new byte[]{period});
        //config.setValue(periodToSend);
        bluetoothGatt.writeCharacteristic(config);
    }

    /*
    public static void turnOnMagnetometer(BluetoothGatt bluetoothGatt) {
        UUID magnetServiceUuid = UUID.fromString("f000aa30-0451-4000-b000-000000000000");
        UUID magnetConfigUuid = UUID.fromString("f000aa32-0451-4000-b000-000000000000");

        BluetoothGattService magnetService = bluetoothGatt.getService(magnetServiceUuid);
        BluetoothGattCharacteristic config = magnetService.getCharacteristic(magnetConfigUuid);
        config.setValue(new byte[]{1}); //NB: the config value is different for the Gyroscope
        bluetoothGatt.writeCharacteristic(config);
    }

    public static void enableMagnetometerNotifications(BluetoothGatt bluetoothGatt) {
        UUID magnetServiceUuid = UUID.fromString("f000aa30-0451-4000-b000-000000000000");
        UUID magnetDataUuid = UUID.fromString("f000aa31-0451-4000-b000-000000000000");
        UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

        BluetoothGattService magnetService = bluetoothGatt.getService(magnetServiceUuid);
        BluetoothGattCharacteristic magnetDataCharacteristic = magnetService.getCharacteristic(magnetDataUuid);
        bluetoothGatt.setCharacteristicNotification(magnetDataCharacteristic, true); //Enabled locally

        BluetoothGattDescriptor config = magnetDataCharacteristic.getDescriptor(CCC);
        config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(config); //Enabled remotely
    }
    */
}