package com.example.nucleoble2;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    // UUIDs des services et caractéristiques BLE
    private static final UUID SERVICE_UUID = UUID.fromString("00000000-0001-11E1-AC36-0002A5D5C51B"); // UUID de votre service
    private static final UUID TEMP_UUID = UUID.fromString("00040000-0001-11E1-AC36-0002A5D5C51B"); // UUID de température

    // Codes de permissions
    private static final int PERMISSION_REQUEST_CODE = 100;

    // Objets principaux
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic tempCharacteristic;
    private Handler handler = new Handler();

    // UI
    private TextView textViewTemperature;
    private TextView textViewStatus;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("OnCreate", "Ouverture de l'appli");
        // --- Création du Layout en Java ---
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        textViewStatus = new TextView(this);
        textViewStatus.setText("Statut: En attente de connexion BLE...");
        textViewStatus.setTextSize(18);
        layout.addView(textViewStatus);

        textViewTemperature = new TextView(this);
        textViewTemperature.setText("Température : --");
        textViewTemperature.setTextSize(24);
        layout.addView(textViewTemperature);

        scrollView.addView(layout);
        setContentView(R.layout.activity_main);

        // --- Initialisation BLE avec permissions ---
        if (checkPermissions()) {
            Log.d("OnCreate", "InitialisationBLE");
            initializeBLE();
        } else {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    private void initializeBLE() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            textViewStatus.setText("Erreur: Bluetooth non activé.");
            Toast.makeText(this, "Bluetooth non activé", Toast.LENGTH_SHORT).show();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        scanAndConnect();
    }

    // --- Scan BLE actif ---
    private void scanAndConnect() {
        textViewStatus.setText("Recherche active de l'appareil...");
        Toast.makeText(this, "Scan BLE en cours...", Toast.LENGTH_SHORT).show();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        bluetoothLeScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device.getName() != null && device.getName().equals("Nucleo-TARQUINY")) {
                    textViewStatus.setText("Appareil trouvé : " + device.getName());
                    Log.d("BLE_SCAN", "Appareil trouvé : " + device.getName());
                    bluetoothLeScanner.stopScan(this);
                    connectToDevice(device);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                textViewStatus.setText("Échec du scan : " + errorCode);
                Log.e("BLE_SCAN", "Échec du scan avec le code : " + errorCode);
            }
        });
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }
        textViewStatus.setText("Connexion en cours...");
        Log.d("BLE_CONNECT", "Connexion à l'appareil : " + device.getName());
        Toast.makeText(this, "Connexion à : " + device.getName(), Toast.LENGTH_SHORT).show();
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("Conenxion", "Connexion BLE: ");
                System.out.println("connecté par BLE !");
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connecté, découverte des services...", Toast.LENGTH_SHORT).show());

                runOnUiThread(() -> textViewStatus.setText("Connecté. Recherche des services..."));
                Log.d("BLE_GATT", "Connecté. Découverte des services en cours...");
                handler.postDelayed(() -> gatt.discoverServices(), 1000);
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread(() -> textViewStatus.setText("Déconnecté. Redémarrage..."));
                Log.d("BLE_GATT", "Déconnecté. Relance du scan...");
                Toast.makeText(MainActivity.this, "Déconnecté. Relance du scan...", Toast.LENGTH_SHORT).show();
                scanAndConnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "onServicesDiscovered appelé, statut : " + status, Toast.LENGTH_SHORT).show());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    tempCharacteristic = service.getCharacteristic(TEMP_UUID);
                    if (tempCharacteristic != null) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Caractéristique trouvée. Lecture en cours...", Toast.LENGTH_SHORT).show());
                        gatt.readCharacteristic(tempCharacteristic);
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Caractéristique non trouvée.", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Service non trouvé.", Toast.LENGTH_SHORT).show());
                }
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Échec onServicesDiscovered, statut : " + status, Toast.LENGTH_SHORT).show());
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("BLE", "Valeur lue : " + characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0));

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(TEMP_UUID)) {
                    float temperature = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0);
                    Log.d("BLE_GATT", "Température lue : " + temperature);
                    runOnUiThread(() -> textViewTemperature.setText(String.format("Température : %.2f °C", temperature)));
                    Toast.makeText(MainActivity.this, "Température : " + temperature + "°C", Toast.LENGTH_SHORT).show();
                }
                Log.e("BLE_GATT", "Échec de la lecture de la caractéristique. Code : " + status);
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}

