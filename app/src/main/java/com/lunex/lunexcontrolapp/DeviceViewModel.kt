package com.lunex.lunexcontrolapp

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    // Bluetooth related members
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    val discoveredDevices = MutableLiveData<List<BluetoothDevice>>()
    val isBluetoothEnabled = MutableLiveData<Boolean>()
    val isConnected = MutableLiveData<Boolean>().apply { value = false }
    private var primaryDevice: BluetoothDevice? = null
    val devices = mutableListOf<BluetoothDevice>()
    private val connectedGatts: MutableMap<String, BluetoothGatt> = mutableMapOf()
    private val _selectedDeviceAddresses = MutableLiveData<Set<String>>(setOf())
    val selectedDeviceAddresses: LiveData<Set<String>> = _selectedDeviceAddresses
    var selectedDevice: BluetoothDevice? = null
        private set
    private val deviceCustomNames = mutableMapOf<String, String>()
    private val sharedPreferences = application.getSharedPreferences("DeviceNames", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val _connectedDevices = MutableLiveData<List<BluetoothDevice>>()
    val connectedDevices: LiveData<List<BluetoothDevice>> = _connectedDevices
    private val resetDataRunnable = Runnable {
        temperature.postValue("-- °C")
        humidity.postValue("-- %")
        lampWhiteState.postValue(false)
        lampYellowState.postValue(false)
    }

    // WiFi related members
    private var espIpAddress: String = "http://192.168.4.1"
    private val retrofit: Retrofit
        get() = Retrofit.Builder()
            .baseUrl(espIpAddress)
            .client(OkHttpClient.Builder().build())  // Add this line
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    private val api: ApiService
        get() = retrofit.create(ApiService::class.java)
    private val _wifiDevices = MutableLiveData<List<ESPDevice>>()
    val wifiDevices: LiveData<List<ESPDevice>> get() = _wifiDevices
    private val prefsManager = SharedPreferencesManager(application)
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Shared members
    val lampWhiteState = MutableLiveData<Boolean>().apply { value = false }
    val lampYellowState = MutableLiveData<Boolean>().apply { value = false }
    val bluetoothStateMessage = MutableLiveData<String?>()
    val requestPermissionsEvent = MutableLiveData<Boolean>()
    val temperature = MutableLiveData<String>()
    val humidity = MutableLiveData<String>()
    private var latestTemperature: String? = null
    private var latestHumidity: String? = null
    private var latestWhiteLampState: Boolean? = null
    private var latestYellowLampState: Boolean? = null

    private lateinit var database: DatabaseReference

    init {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        updateBluetoothState()
        loadCustomNames()
        temperature.postValue("-- °C")
        humidity.postValue("-- %")
        lampWhiteState.postValue(false)
        lampYellowState.postValue(false)
        _wifiDevices.value = prefsManager.getDevices()

        // Initialize Firebase
        FirebaseApp.initializeApp(application)
        database = FirebaseDatabase.getInstance().reference
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805f9b34fb")
    }

    private fun updateBluetoothState() {
        isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
        Log.d("DeviceViewModel", "${bluetoothAdapter?.isEnabled}")
    }

    private fun loadCustomNames() {
        sharedPreferences.all.forEach { entry ->
            deviceCustomNames[entry.key] = entry.value.toString()
        }
    }

    fun saveCustomName(deviceAddress: String, customName: String) {
        deviceCustomNames[deviceAddress] = customName
        sharedPreferences.edit().putString(deviceAddress, customName).apply()
    }

    fun toggleDeviceSelection(deviceAddress: String) {
        val currentSelection = selectedDeviceAddresses.value?.toMutableSet() ?: mutableSetOf()
        if (currentSelection.contains(deviceAddress)) {
            currentSelection.remove(deviceAddress)
        } else {
            currentSelection.add(deviceAddress)
        }
        _selectedDeviceAddresses.value = currentSelection
    }

    fun scanForDevices() {
        val context = getApplication<Application>().applicationContext
        // Check Bluetooth enabled status
        if (bluetoothAdapter?.isEnabled != true) {
            bluetoothStateMessage.postValue("O Bluetooth do seu aparelho está desligado, por favor ative-o.")
            return
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsEvent.postValue(true)
            return
        }

        clearDiscoveredDevices()
        bluetoothLeScanner?.startScan(scanCallback)
    }

    fun clearDiscoveredDevices() {
        devices.clear() // Clear the internal list
        discoveredDevices.postValue(emptyList()) // Update LiveData
    }

    fun updateConnectedDevicesList() {
        _connectedDevices.postValue(connectedGatts.map { it.value.device })
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val context = getApplication<Application>().applicationContext
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (device.name == "SmartLight" && !devices.contains(device)) {
                    Log.d("ScanCallback", "Device found: ${result.device.address}")
                    devices.add(device)
                    discoveredDevices.postValue(devices)
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { result ->
                val device = result.device
                val context = getApplication<Application>().applicationContext
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    if (device.name == "SmartLight" && !devices.contains(device)) {
                        devices.add(device)
                    }
                }
            }
            discoveredDevices.postValue(devices)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("ScanCallback", "Scan failed with error: $errorCode")
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectAllDevices()
        handler.removeCallbacks(resetDataRunnable)
    }

    fun connectToDevice(context: Context, device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val gatt = device.connectGatt(context, false, gattCallback)
            connectedGatts[device.address] = gatt
            updateConnectedDevicesList()
            if (primaryDevice == null) {
                primaryDevice = device
            }
            Log.d("DeviceViewModel", "Connecting to: ${device.address}")
        } else {
            bluetoothStateMessage.postValue("Permission BLUETOOTH_CONNECT not granted")
            Log.e("DeviceViewModel", "Permission BLUETOOTH_CONNECT not granted")
        }
    }

    fun disconnectAllDevices() {
        val context = getApplication<Application>().applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            connectedGatts.values.forEach { gatt ->
                gatt.disconnect()
                gatt.close()
                Log.d("DeviceViewModel", "Disconnecting and closing GATT for: ${gatt.device.address}")
            }
            connectedGatts.clear()
            primaryDevice = null
            isConnected.postValue(false)
            updateConnectedDevicesList() // Refresh the list whenever a device is disconnected
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val context = getApplication<Application>().applicationContext

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("DeviceViewModel", "Device connected: ${gatt.device.address}")
                        isConnected.postValue(true)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("DeviceViewModel", "Device disconnected: ${gatt.device.address}")
                        isConnected.postValue(false)
                        connectedGatts.remove(gatt.device.address)
                        if (primaryDevice?.address == gatt.device.address) {
                            primaryDevice = connectedGatts.keys.firstOrNull()?.let { address ->
                                connectedGatts[address]?.device
                            }
                        }
                        gatt.close()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val context = getApplication<Application>().applicationContext
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic != null) {
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            descriptor?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            val value = characteristic.value
            value?.let {
                val data = String(it, Charsets.UTF_8)
                updateData(data, gatt.device)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("DeviceViewModel", "Characteristic write successful: ${characteristic.uuid}")
            } else {
                Log.e("DeviceViewModel", "Characteristic write failed: ${characteristic.uuid}, status: $status")
            }
        }
    }

//    fun sendCommandToAllDevices(command: String) {
//        // Send command via BLE
//        if (connectedGatts.isNotEmpty()) {
//            connectedGatts.values.forEach { gatt ->
//                sendCommandToDevice(gatt, command)
//            }
//        }
//        // Send command via WiFi
//        else if (_wifiDevices.value?.isNotEmpty() == true) {
//            _wifiDevices.value?.filter { it.isSelected }?.forEach { device ->
//                val call = api.sendCommand(command)
//                call.enqueue(object : Callback<Void> {
//                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
//                        if (response.isSuccessful) {
//                            Log.d("Command", "Command sent successfully to ${device.id}")
//                        } else {
//                            Log.d("Command", "Failed to send command to ${device.id}")
//                        }
//                    }
//
//                    override fun onFailure(call: Call<Void>, t: Throwable) {
//                        Log.e("Command", "Error: ${t.message}")
//                    }
//                })
//            }
//        }
//        // Send command via Firebase
//        else {
//            sendCommandToFirebase(command)
//        }
//    }

    private fun sendCommandToFirebase(command: String) {
        database.child("commands").setValue(command)
            .addOnSuccessListener {
                Log.d("Firebase", "Command sent successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Failed to send command", e)
            }
    }


    fun sendCommand(command: String) {
        _wifiDevices.value?.filter { it.isSelected }?.forEach { device ->
            val call = api.sendCommand(command)
            call.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Log.d("Command", "Command sent successfully to ${device.id}")
                    } else {
                        Log.d("Command", "Failed to send command to ${device.id}")
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e("Command", "Error: ${t.message}")
                }
            })
        }
    }

    private fun sendCommandToDevice(gatt: BluetoothGatt, command: String) {
        val serviceUUID = UUID.fromString(SERVICE_UUID.toString())
        val characteristicUUID = UUID.fromString(CHARACTERISTIC_UUID.toString())
        val service = gatt.getService(serviceUUID)
        val context = getApplication<Application>().applicationContext
        service?.getCharacteristic(characteristicUUID)?.let { characteristic ->
            characteristic.value = command.toByteArray(Charsets.UTF_8)
            if (ContextCompat.checkSelfPermission(
                    context,
                    BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (gatt.writeCharacteristic(characteristic)) {
                    Log.d(
                        "DeviceViewModel",
                        "Command $command sent to device ${gatt.device.address}"
                    )
                } else {
                    Log.e(
                        "DeviceViewModel",
                        "Failed to send command $command to device ${gatt.device.address}"
                    )
                }
            }
        }
    }

    fun getDeviceCustomName(deviceAddress: String, defaultName: String?): String {
        return deviceCustomNames[deviceAddress] ?: defaultName ?: "Unknown Device"
    }

    fun updateData(data: String, device: BluetoothDevice) {
        if (device != primaryDevice) return

        Log.d("DeviceViewModel", "Received data: $data")

        if (data.startsWith("T:") && data.contains("U:")) {
            latestTemperature = data.substringAfter("T:").substringBefore("º").trim()
            latestHumidity = data.substringAfter("U:").substringBefore("%").trim()
            Log.d("DeviceViewModel", "Parsed temperature: $latestTemperature, humidity: $latestHumidity")
        }

        if (data.startsWith("H:") && data.contains("C:")) {
            val warmValue = data.substringAfter("H:").substringBefore(';').trim().toIntOrNull() ?: 0
            val coldValue = data.substringAfter("C:").trim().toIntOrNull() ?: 0
            latestWhiteLampState = coldValue > 0
            latestYellowLampState = warmValue > 0
            Log.d("DeviceViewModel", "Parsed white lamp state: $latestWhiteLampState, yellow lamp state: $latestYellowLampState")
        }

        if (latestTemperature != null && latestHumidity != null && latestWhiteLampState != null && latestYellowLampState != null) {
            Log.d("DeviceViewModel", "Parsed temp: $latestTemperature, humi: $latestHumidity, whiteLampState: $latestWhiteLampState, yellowLampState: $latestYellowLampState")

            if (_connectedDevices.value?.size == 1) {
                temperature.postValue("$latestTemperature °C")
                humidity.postValue("$latestHumidity %")
            } else {
                temperature.postValue("-- °C")
                humidity.postValue("-- %")
            }

            lampWhiteState.postValue(latestWhiteLampState)
            lampYellowState.postValue(latestYellowLampState)

            // Clear latest values after updating the UI
            latestTemperature = null
            latestHumidity = null
            latestWhiteLampState = null
            latestYellowLampState = null
        }

        // Reset temperature and humidity to default "--" values after 5 seconds, without interfering with immediate updates
        handler.removeCallbacks(resetDataRunnable)
        handler.postDelayed(resetDataRunnable, 5000)
    }

    fun updateIpAddress(newIpAddress: String) {
        espIpAddress = "http://$newIpAddress"
        Log.d("WiFiDebug", "Updated ESP IP address to: $espIpAddress")
    }

    fun connectToWiFi(ssid: String, password: String) {
        val call = api.connectToWiFi(ssid, password)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("WiFi", "Connected successfully")
                    val newIpAddress = extractIpAddressFromResponse(response)
                    updateIpAddress(newIpAddress)
                    getDeviceInfo()
                } else {
                    Log.d("WiFi", "Failed to connect")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("WiFi", "Error: ${t.message}")
            }
        })
    }

    private fun extractIpAddressFromResponse(response: Response<Void>): String {
        // Extract the IP address from the response if your ESP sends it
        // This is just a placeholder, actual implementation depends on your response format
        return "extracted_ip_address"
    }

    // Method to connect to WiFi and update IP address
    fun connectToWiFiAndFetchIp(ssid: String, password: String) {
        val call = api.connectToWiFi(ssid, password)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("WiFiDebug", "Connected successfully")
                    getDeviceInfo()
                } else {
                    Log.d("WiFiDebug", "Failed to connect")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("WiFiDebug", "Error: ${t.message}")
            }
        })
    }

    private fun getDeviceInfo() {
        val call = api.getDeviceInfo()
        call.enqueue(object : Callback<DeviceInfo> {
            override fun onResponse(call: Call<DeviceInfo>, response: Response<DeviceInfo>) {
                if (response.isSuccessful) {
                    val deviceInfo = response.body()
                    deviceInfo?.let {
                        // Update the IP address dynamically
                        updateIpAddress(it.ip)
                    }
                } else {
                    Log.d("DeviceInfo", "Failed to get device info")
                }
            }

            override fun onFailure(call: Call<DeviceInfo>, t: Throwable) {
                Log.e("DeviceInfo", "Error: ${t.message}")
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun waitForWiFiConnection(): Boolean {
        for (i in 0 until 20) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true
            }
            Thread.sleep(1000)
        }
        return false
    }
}
