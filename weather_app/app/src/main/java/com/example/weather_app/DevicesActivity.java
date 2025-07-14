package com.example.weather_app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Collections;

public class DevicesActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ArrayAdapter<String> devicesArrayAdapter;
    private ArrayList<BluetoothDevice> scannedDevices = new ArrayList<>();
    private ListView devicesListView;

    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && !scannedDevices.contains(device)) {
                scannedDevices.add(device);

                String name = "Unknown device";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(DevicesActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        if (device.getName() != null) {
                            name = device.getName();
                        }
                    }
                } else {
                    if (device.getName() != null) {
                        name = device.getName();
                    }
                }

                devicesArrayAdapter.add(name + "\n" + device.getAddress());
                devicesArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        devicesListView = findViewById(R.id.devices_list);
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        devicesListView.setAdapter(devicesArrayAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth nu este suportat pe acest dispozitiv", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();


        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = true;
                    for (Boolean isGranted : result.values()) {
                        if (!isGranted) {
                            granted = false;
                            break;
                        }
                    }
                    if (granted) {
                        checkBluetoothAndScan();
                    } else {
                        Toast.makeText(this, "Permisiunile Bluetooth sunt necesare pentru scanare", Toast.LENGTH_SHORT).show();
                    }
                }
        );


        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (bluetoothAdapter.isEnabled()) {
                        startBleScan();
                    } else {
                        Toast.makeText(this, "Bluetooth este necesar pentru scanare", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = scannedDevices.get(position);
            connectToDevice(device);
        });

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toArray(new String[0]));
        } else {
            checkBluetoothAndScan();
        }
    }

    private void checkBluetoothAndScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        } else {
            startBleScan();
        }
    }

    private void startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permisiunile BLUETOOTH_SCAN și ACCESS_FINE_LOCATION nu sunt acordate", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        devicesArrayAdapter.clear();
        scannedDevices.clear();

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bleScanner.startScan(Collections.emptyList(), scanSettings, scanCallback);
        Toast.makeText(this, "Scanare BLE pornită", Toast.LENGTH_SHORT).show();
    }

    private void stopBleScan() {
        if (bleScanner != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bleScanner.stopScan(scanCallback);
                }
            } else {
                bleScanner.stopScan(scanCallback);
            }
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permisiunea BLUETOOTH_CONNECT nu este acordată", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        stopBleScan();

        Intent intent = new Intent(this, WeatherActivity.class);
        intent.putExtra("device_address", device.getAddress());
        startActivity(intent);

        Toast.makeText(this, "Conectare la " + (device.getName() != null ? device.getName() : "Unknown device"), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBleScan();
    }
}
