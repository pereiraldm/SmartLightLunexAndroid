package com.lunex.lunexcontrolapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.UUID
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    val discoveredDevices = MutableLiveData<List<BluetoothDevice>>()
    val isBluetoothEnabled = MutableLiveData<Boolean>()
    val isConnected = MutableLiveData<Boolean>().apply { value = false }
    private var primaryDevice: BluetoothDevice? = null
    private val devices = mutableListOf<BluetoothDevice>()
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
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805f9b34fb")
    }

    private fun updateBluetoothState() {
        isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
        Log.d("BluetoothViewModel", "${bluetoothAdapter?.isEnabled}")
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
        if (ActivityCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsEvent.postValue(true)
            return
        }

        clearDiscoveredDevices()
        bluetoothLeScanner?.startScan(scanCallback)
    }

    fun setSelectedDevice(device: BluetoothDevice?) {
        selectedDevice = device
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
            if (ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
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
                if (ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    if (device.name == "SmartLight" && !devices.contains(device)) {
                        devices.add(device)
                    }
                }
            }
            discoveredDevices.postValue(devices)
        }

    }

    override fun onCleared() {
        super.onCleared()
        disconnectAllDevices()
        handler.removeCallbacks(resetDataRunnable)
    }

    fun connectToDevice(context: Context, device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val gatt = device.connectGatt(context, false, gattCallback)
            connectedGatts[device.address] = gatt
            updateConnectedDevicesList()
            if (primaryDevice == null) {
                primaryDevice = device
            }
            Log.d("BluetoothViewModel", "Connecting to: ${device.address}")
        } else {
            bluetoothStateMessage.postValue("Permission BLUETOOTH_CONNECT not granted")
            Log.e("BluetoothViewModel", "Permission BLUETOOTH_CONNECT not granted")
        }
    }

    fun disconnectAllDevices() {
        val context = getApplication<Application>().applicationContext
        if (ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            connectedGatts.values.forEach { gatt ->
                gatt.disconnect()
                gatt.close()
                Log.d("BluetoothViewModel", "Disconnecting and closing GATT for: ${gatt.device.address}")
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

            if (ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("BluetoothViewModel", "Device connected: ${gatt.device.address}")
                        isConnected.postValue(true)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("BluetoothViewModel", "Device disconnected: ${gatt.device.address}")
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
            if (ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic != null) {
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor =
                                characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
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
                // Log.d("BluetoothViewModel", "Characteristic changed: $data")
                updateData(data, gatt.device)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothViewModel", "Characteristic write successful: ${characteristic.uuid}")
            } else {
                Log.e("BluetoothViewModel", "Characteristic write failed: ${characteristic.uuid}, status: $status")
            }
        }
    }

    fun sendCommandToAllDevices(command: String) {
        connectedGatts.values.forEach { gatt ->
            sendCommandToDevice(gatt, command)
        }
    }

    private fun sendCommandToDevice(gatt: BluetoothGatt, command: String) {
        val serviceUUID = UUID.fromString(SERVICE_UUID.toString())
        val characteristicUUID = UUID.fromString(CHARACTERISTIC_UUID.toString())
        val service = gatt.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)
        val context = getApplication<Application>().applicationContext
        if (ContextCompat.checkSelfPermission(context, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            gatt.getService(serviceUUID)?.getCharacteristic(characteristicUUID)?.let { characteristic ->
                characteristic.value = command.toByteArray(Charsets.UTF_8)
                if (gatt.writeCharacteristic(characteristic)) {
                    Log.d("BluetoothViewModel", "Command $command sent to device ${gatt.device.address}")
                } else {
                    Log.e("BluetoothViewModel", "Failed to send command $command to device ${gatt.device.address}")
                }
            }
        }
    }

    fun getDeviceCustomName(deviceAddress: String, defaultName: String?): String {
        return deviceCustomNames[deviceAddress] ?: defaultName ?: "Unknown Device"
    }

    fun updateData(data: String, device: BluetoothDevice) {
        if (device != primaryDevice) return

        Log.d("BluetoothViewModel", "Received data: $data")

        if (data.startsWith("T:") && data.contains("U:")) {
            latestTemperature = data.substringAfter("T:").substringBefore("º").trim()
            latestHumidity = data.substringAfter("U:").substringBefore("%").trim()
            Log.d("BluetoothViewModel", "Parsed temperature: $latestTemperature, humidity: $latestHumidity")
        }

        if (data.startsWith("H:") && data.contains("C:")) {
            val warmValue = data.substringAfter("H:").substringBefore(';').trim().toIntOrNull() ?: 0
            val coldValue = data.substringAfter("C:").trim().toIntOrNull() ?: 0
            latestWhiteLampState = coldValue > 0
            latestYellowLampState = warmValue > 0
            Log.d("BluetoothViewModel", "Parsed white lamp state: $latestWhiteLampState, yellow lamp state: $latestYellowLampState")
        }

        if (latestTemperature != null && latestHumidity != null && latestWhiteLampState != null && latestYellowLampState != null) {
            Log.d("BluetoothViewModel", "Parsed temp: $latestTemperature, humi: $latestHumidity, whiteLampState: $latestWhiteLampState, yellowLampState: $latestYellowLampState")

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


}