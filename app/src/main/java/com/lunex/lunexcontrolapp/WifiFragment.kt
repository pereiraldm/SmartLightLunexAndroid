package com.lunex.lunexcontrolapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log

class WifiFragment : Fragment() {

    private lateinit var viewModel: DeviceViewModel
    private lateinit var devicesAdapter: DevicesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wifi, container, false)

        viewModel = ViewModelProvider(this).get(DeviceViewModel::class.java)

        val etSsid = view.findViewById<EditText>(R.id.et_ssid)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val btnConnect = view.findViewById<Button>(R.id.btn_connect)
        val etCommand = view.findViewById<EditText>(R.id.et_command)
        val btnSendCommand = view.findViewById<Button>(R.id.btn_send_command)
        val rvDevices = view.findViewById<RecyclerView>(R.id.rv_devices)

        devicesAdapter = DevicesAdapter(viewModel)
        rvDevices.layoutManager = LinearLayoutManager(context)
        rvDevices.adapter = devicesAdapter

        btnConnect.setOnClickListener {
            val ssid = etSsid.text.toString()
            val password = etPassword.text.toString()
            viewModel.connectToWiFi(ssid, password)
        }

        btnSendCommand.setOnClickListener {
            val command = etCommand.text.toString()
            viewModel.sendCommand(command)
        }

//        viewModel.devices.observe(viewLifecycleOwner) { devices ->
//            devicesAdapter.submitList(devices)
//        }

        return view
    }
}

