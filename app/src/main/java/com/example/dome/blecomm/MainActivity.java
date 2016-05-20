package com.example.dome.blecomm;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Created by Dome on 18.05.2016.
 */
public class MainActivity extends Activity {


    // UUIDs for UAT service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UI
    private TextView messages;
    private EditText input;

    // BLE
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;

    //BTL Callback
    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String request = characteristic.getStringValue(0);
            writeLine("Received: " + characteristic.getStringValue(0));
            switch (request.charAt(0)){
                case 'T': sendTime(request.substring(1));
                case 'U': getUV(request.substring(1));
            }

        }

        //Called when servies are discovered
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeLine("Service discovery completed");
            } else {
                writeLine("Service discovery failed with status " + status);
            }
            // save characteristic referenze
            tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
            rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);

            // Setup notifications on RX characteristic changes (i.e. data received).
            // First call setCharacteristicNotification to enable notification.
            if (!gatt.setCharacteristicNotification(rx,true)){
                writeLine("couldnt set notifications for RX charactersitc!");
            }
            // Next update the RX characteristic's client descriptor to enable notifications.
            if (rx.getDescriptor(CLIENT_UUID) != null){
                BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!gatt.writeDescriptor(desc)){
                    writeLine("Could not write RX client descriptor values!");
                }
            } else {
                writeLine("Could not get RX client descriptor!");
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED){
                writeLine("Connected!");
                // Discover Services
                if (!gatt.discoverServices()){
                    writeLine("Failed to start discovering services! ");
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED){
                writeLine("Disconnected");
            } else {
                writeLine("Connection state changed. New State : "+ newState);
            }
        }
    };

    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            writeLine("Found device: " + device.getAddress());
            // checks if the device has uart servcie
            if (parseUUIDs(scanRecord).contains(UART_UUID)){
                // device found, stop scan
                adapter.stopLeScan(scanCallback);
                writeLine("Found UART Service !");
                // connect to device
                // Control flow iwll now go to the callback functions when BLE Events occur !
                gatt = device.connectGatt(getApplicationContext(),false,callback);
            }
        }
    };

    //oncreate called to initialize
    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Grab UI referenzes
        messages = (TextView) findViewById(R.id.textView);
        input = (EditText) findViewById(R.id.editText);
        adapter = BluetoothAdapter.getDefaultAdapter(); // DEPRECATED , new version requires API 21!
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
        // Scan for all BTLE devices.
        // The first one with the UART service will be chosen--see the code in the scanCallback.
        writeLine("Scanning for devices...");
        adapter.startLeScan(scanCallback);
    }

    // OnStop, called right before the activity loses foreground focus.  Close the BTLE connection.
    @Override
    protected void onStop() {
        super.onStop();
        if (gatt != null) {
            // For better reliability be careful to disconnect and close the connection.
            gatt.disconnect();
            gatt.close();
            gatt = null;
            tx = null;
            rx = null;
        }
    }

    // Handler for mouse click on the send button.
    public void sendClick(View view) {
        String message = input.getText().toString();
        if (tx == null || message == null || message.isEmpty()) {
            // Do nothing if there is no device or message to send.
            return;
        }
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(message.getBytes(Charset.forName("UTF-8")));
        if (gatt.writeCharacteristic(tx)) {
            writeLine("Sent: " + message);
        }
        else {
            writeLine("Couldn't write TX characteristic!");
        }
    }

    //send data via tx TODO: create generic type / make sure ints wont be sent as strings (more data)
    private boolean sendTX(String value){
        // do nothing if message is empty or no device to send
        if (tx == null || value == null || value.isEmpty()) return false;
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(value.getBytes(Charset.forName("UTF-8")));
        // write
        if (gatt.writeCharacteristic(tx)) {
            writeLine("sent: " +value);
            return true;
        } else return false;
    }


    // Write some text to the messages text view.
    // Care is taken to do this on the main UI thread so writeLine can be called
    // from any thread (like the BTLE callback).
    private void writeLine(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messages.append(text);
                messages.append("\n");
            }
        });
    }

    private boolean sendTime(String timeelement){
        writeLine(timeelement);
        if (timeelement.length() > 1) return false;

        switch (timeelement.charAt(0)){
            // millis to seconds to string and send it
            case 't': {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
                Date resultdate = new Date(System.currentTimeMillis());
               writeLine(sdf.format(resultdate));
                long millis = (long)(System.currentTimeMillis()/1000.0); return sendTX(Long.toString(millis));}
            case 'c':{ writeLine(timeelement.substring(1));
                if ((long)(System.currentTimeMillis()/1000.0) == Long.getLong(timeelement.substring(1)))
                {
                    // time is set correct, respond
                    sendTX("c"); return true;
                } else return false;
                    }
            default: return false;
        }
    }

    // pares UV data , enter in database or display
    private void getUV(String response){
        switch (response.charAt(0)){
            case 'u':{
               // average UV data (time period see device) , store in database
                float avgUV = Float.valueOf(response.substring(1));
                // remember to save timestamp
                writeLine(SimpleDateFormat.getDateTimeInstance().format(new Date(System.currentTimeMillis()))+ " : " + avgUV);
            }
            case 'm': // monitor -> display live ;
        }
    }


    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit,
                                    mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                            continue;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }
        return uuids;
    }

}
