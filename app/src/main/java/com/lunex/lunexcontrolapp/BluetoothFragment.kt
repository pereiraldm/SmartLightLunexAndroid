package com.lunex.lunexcontrolapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lunex.lunexcontrolapp.databinding.FragmentBluetoothBinding

class BluetoothFragment : Fragment() {


    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BluetoothViewModel by activityViewModels()
    private lateinit var adapter: BluetoothDeviceAdapter // Lateinit declared here

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root // Do not set the adapter here
    }

    private fun setupObservers() {
        viewModel.discoveredDevices.observe(viewLifecycleOwner) { devices ->
            adapter.updateDevices(devices)
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent?.action) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> {
                        // Update the icon to indicate Bluetooth is off
                        updateBluetoothIcon(false)
                    }
                    BluetoothAdapter.STATE_ON -> {
                        // Update the icon to indicate Bluetooth is on
                        updateBluetoothIcon(true)
                        viewModel.scanForDevices()
                    }
                }
            }
        }
    }

    private fun updateBluetoothIcon(isEnabled: Boolean) {
        val drawableRes = if (isEnabled) R.drawable.ic_bluetooth_on else R.drawable.ic_bluetooth_off
        binding.imBluetoothOn.setImageResource(drawableRes)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        requireActivity().registerReceiver(bluetoothStateReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        requireActivity().unregisterReceiver(bluetoothStateReceiver)
    }


    private fun requestPermissions() {
        val requiredPermissions = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Para Android 12 ou superior
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            requestPermissions(requiredPermissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    viewModel.scanForDevices()
                } else {
                    showToast("Com as permissões negadas não será possível conectar-se com o dispositivo.")
                }
            }
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, you can proceed with Bluetooth operations
                } else {
                    showToast("Com as permissões negadas não será possível conectar-se com o dispositivo.")
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()
        setupRecyclerView()
        setupObservers()

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothEnabled = bluetoothAdapter?.isEnabled ?: false
        updateBluetoothIcon(isBluetoothEnabled)

        binding.imBluetoothOn.setOnClickListener {
            val message = if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
                "O Bluetooth do seu aparelho está ativado!"
            } else {
                "O Bluetooth do seu aparelho está desativado, por favor ative-o."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        if (!::adapter.isInitialized) {
            adapter = BluetoothDeviceAdapter(
                context = requireContext(),
                onDeviceSelectionChanged = {
                    binding.connect.isEnabled = adapter.getSelectedDevices().isNotEmpty()
                },
                saveCustomName = { address, name ->
                    viewModel.saveCustomName(address, name)
                }
            )
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(context)
        binding.rvDevices.adapter = adapter

        setupObservers()

        // Configura o observador para o estado de conexão
        viewModel.isConnected.observe(viewLifecycleOwner) { isConnected ->
            // Atualiza o texto do botão
            binding.connect.text = if (isConnected) "DESCONECTAR" else "CONECTAR"
            updateConnectButton(isConnected)
            // Atualiza a cor do botão
            val color = if (isConnected) R.color.red else R.color.green
            binding.connect.backgroundTintList = ContextCompat.getColorStateList(requireContext(), color)
        }

        binding.btnScan.setOnClickListener {
            if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
                viewModel.clearDiscoveredDevices()
                viewModel.scanForDevices()
            } else {
                showToast("O Bluetooth do seu aparelho está desligado, por favor ative-o.")
            }
        }

        binding.connect.setOnClickListener {
            if (viewModel.isConnected.value == true) {
                viewModel.disconnectAllDevices()
            } else {
                adapter.getSelectedDevices().forEach { address ->
                    val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
                    viewModel.connectToDevice(requireContext(), device)
                }
            }
        }

        viewModel.bluetoothStateMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                showToast(it)
                viewModel.bluetoothStateMessage.value = null // Reset after showing
            }
        }

        viewModel.selectedDeviceAddresses.observe(viewLifecycleOwner) { selectedAddresses ->
            adapter.setSelectedDevices(selectedAddresses)
            binding.connect.isEnabled = selectedAddresses.isNotEmpty()
        }

        viewModel.isConnected.observe(viewLifecycleOwner) { isConnected ->
            updateConnectButton(isConnected)
        }

        viewModel.requestPermissionsEvent.observe(viewLifecycleOwner) { request ->
            if (request) {
                requestBluetoothPermissions() // Your method to request permissions
                viewModel.requestPermissionsEvent.value = false // Reset after handling
            }
        }

        // Ensure the icon and connect button states are updated to reflect current Bluetooth state
        updateBluetoothStateUI(BluetoothAdapter.getDefaultAdapter().isEnabled)
    }

    private fun requestBluetoothPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_BLUETOOTH_PERMISSIONS
        )
    }

    private fun setupRecyclerView() {
        adapter = BluetoothDeviceAdapter(
            context = requireContext(),
            onDeviceSelectionChanged = {
                viewModel.toggleDeviceSelection(it) // Adjust this line as needed
                binding.connect.isEnabled = adapter.getSelectedDevices().isNotEmpty()
            },
            saveCustomName = { address, name ->
                viewModel.saveCustomName(address, name)
            }
        )

        binding.rvDevices.layoutManager = LinearLayoutManager(context)
        binding.rvDevices.adapter = adapter
    }

    private fun updateConnectButton(isConnected: Boolean) {
        binding.connect.text = if (isConnected) "DESCONECTAR" else "CONECTAR"
        val color = if (isConnected) R.color.red else R.color.green
        binding.connect.backgroundTintList = ContextCompat.getColorStateList(requireContext(), color)
    }

    private fun updateBluetoothStateUI(isBluetoothEnabled: Boolean) {
        // Update the Bluetooth icon based on the current state
        updateBluetoothIcon(isBluetoothEnabled)
        // Disable connect and scan buttons if Bluetooth is off
        binding.connect.isEnabled = isBluetoothEnabled && viewModel.selectedDevice != null
        binding.btnScan.isEnabled = isBluetoothEnabled
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.selectedDeviceAddresses.observe(viewLifecycleOwner) { addresses ->
            adapter.setSelectedDevices(addresses)
            updateConnectButton(viewModel.isConnected.value ?: false)
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2
    }
}