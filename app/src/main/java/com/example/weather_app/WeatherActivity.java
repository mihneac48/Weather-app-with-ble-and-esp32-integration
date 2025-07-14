package com.example.weather_app;

import android.Manifest;
import android.bluetooth.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import org.json.JSONException;
import org.json.JSONObject;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class WeatherActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_CONNECT = 101;

    private EditText cityInput;
    private Button searchButton;
    private TextView temperatureText, windText;

    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;

    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;

    private static final UUID SERVICE_UUID = UUID.fromString("01fad59e-bb28-4953-a6d7-43a842bb0f83");
    private static final UUID CHARACTERISTIC_RX_UUID = UUID.fromString("9311449e-e41b-4a28-a584-eed29d9ddc63");
    private static final UUID CHARACTERISTIC_TX_UUID = UUID.fromString("84eb6680-dac7-4444-9ab3-e0159e51d1a7");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        cityInput = findViewById(R.id.city_input);
        searchButton = findViewById(R.id.search_button);
        temperatureText = findViewById(R.id.temperature_text);
        windText = findViewById(R.id.wind_text);

        String deviceAddress = getIntent().getStringExtra("device_address");
        if (deviceAddress == null) {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
            return;
        }

        device = adapter.getRemoteDevice(deviceAddress);
        connectToDevice();

        searchButton.setOnClickListener(v -> {
            String city = cityInput.getText().toString().trim();
            if (!TextUtils.isEmpty(city)) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(cityInput.getWindowToken(), 0);
                }
                sendCityToESP(city);
            } else {
                Toast.makeText(this, "Introduceți un oraș", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void connectToDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private void sendCityToESP(String city) {
        if (writeCharacteristic != null) {
            writeCharacteristic.setValue(city.getBytes(StandardCharsets.UTF_8));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.writeCharacteristic(writeCharacteristic);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(WeatherActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                gatt.discoverServices();
                runOnUiThread(() -> Toast.makeText(WeatherActivity.this, "Conectat la dispozitiv", Toast.LENGTH_SHORT).show());
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread(() ->
                        Toast.makeText(WeatherActivity.this, "Deconectat de la dispozitiv", Toast.LENGTH_SHORT).show());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_RX_UUID);
                notifyCharacteristic = service.getCharacteristic(CHARACTERISTIC_TX_UUID);

                if (notifyCharacteristic != null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ActivityCompat.checkSelfPermission(WeatherActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

                        gatt.setCharacteristicNotification(notifyCharacteristic, true);

                        BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_TX_UUID.equals(characteristic.getUuid())) {
                String response = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                runOnUiThread(() -> handleResponse(response));
            }
        }
    };

    private void handleResponse(String data) {
        try {
            JSONObject obj = new JSONObject(data);
            double temp = obj.getDouble("t");
            double wind = obj.getDouble("v");

            temperatureText.setText("Temperatură: " + temp + " °C");
            windText.setText("Vânt: " + wind + " km/h");

        } catch (JSONException e) {
            temperatureText.setText("Date vreme indisponibile");
            windText.setText("");
            Log.e("BLE", "Eroare parsare JSON: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice();
            } else {
                Toast.makeText(this, "Permisiunea Bluetooth a fost refuzată", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
