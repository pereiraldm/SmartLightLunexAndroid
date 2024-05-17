package com.lunex.lunexcontrolapp

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class BluetoothDeviceAdapter(
    private val context: Context,
    private val onDeviceSelectionChanged: (String) -> Unit,  // Changed from () -> Unit
    private val saveCustomName: (String, String) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder>() {

    private var devices: List<BluetoothDevice> = listOf()
    private var selectedDevices: MutableSet<String> = mutableSetOf()
    private val sharedPreferences = context.getSharedPreferences("DeviceNames", Context.MODE_PRIVATE)

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    // This method returns a set of selected device addresses
    fun getSelectedDevices(): Set<String> {
        return selectedDevices
    }

    fun setSelectedDevices(addresses: Set<String>) {
        selectedDevices.clear()
        selectedDevices.addAll(addresses)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bluetooth_device_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device, selectedDevices.contains(device.address)) {
            // Toggle device selection
            if (selectedDevices.contains(device.address)) {
                selectedDevices.remove(device.address)
            } else {
                selectedDevices.add(device.address)
            }
            onDeviceSelectionChanged(device.address) // Pass the device address back to the fragment
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = devices.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val deviceName: TextView = view.findViewById(R.id.device_name)
        private val checkBox: CheckBox = view.findViewById(R.id.device_checkbox)
        private val editDeviceName: ImageView = view.findViewById(R.id.edit_device_name)

        fun bind(device: BluetoothDevice, isSelected: Boolean, onClick: () -> Unit) {
            val customName = getCustomName(device.address)
            deviceName.text = customName
            checkBox.isChecked = isSelected
            itemView.setOnClickListener { onClick() }
            checkBox.setOnClickListener { onClick() }
            editDeviceName.setOnClickListener { showRenameDialog(device) }
        }

        private fun getCustomName(deviceAddress: String): String {
            return sharedPreferences.getString(deviceAddress, null) ?: "SmartLight"
        }

        private fun showRenameDialog(device: BluetoothDevice) {
            val editText = EditText(context).apply {
                hint = "Novo nome do dispositivo"
                setText(getCustomName(device.address)) // Pre-fill with current custom name if exists
            }
            AlertDialog.Builder(context)
                .setTitle("Renomeie o dispositivo")
                .setView(editText)
                .setPositiveButton("Salvar") { _, _ ->
                    val newName = editText.text.toString()
                    if (newName.isNotBlank()) {
                        saveCustomName(device.address, newName)
                        deviceName.text = newName
                        Toast.makeText(context, "Dispositivo renomeado para: $newName", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}

