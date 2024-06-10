package com.lunex.lunexcontrolapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView


class DevicesAdapter(private val viewModel: DeviceViewModel) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    private var devices: List<ESPDevice> = listOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount(): Int = devices.size

    fun submitList(newDevices: List<ESPDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.cb_device)

        fun bind(device: ESPDevice) {
            checkBox.text = device.id
            checkBox.isChecked = device.isSelected

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                device.isSelected = isChecked
            }
        }
    }
}

