package com.lunex.lunexcontrolapp

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class InicialFragment : Fragment() {

    private lateinit var bluetoothStatusImageView: ImageView
    private lateinit var connectionStatusTextView: TextView
    private lateinit var btnToggle: AppCompatButton
    private lateinit var btnFrio: Button
    private lateinit var btnNeutro: Button
    private lateinit var btnQuente: Button
    private lateinit var lampWhite: ImageView
    private lateinit var lampYellow: ImageView
    private val viewModel: BluetoothViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_inicial, container, false)

        bluetoothStatusImageView = view.findViewById(R.id.bluetoothStatusImageView)
        connectionStatusTextView = view.findViewById(R.id.connectionStatusTextView)

        setupUI()
        return view
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent?.action) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d("InicialFragment", "Bluetooth turned off")
                        updateBluetoothIcon(false)
                    }
                    BluetoothAdapter.STATE_ON -> {
                        Log.d("InicialFragment", "Bluetooth turned on")
                        updateBluetoothIcon(true)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        activity?.registerReceiver(bluetoothStateReceiver, filter)

        // Ensure the icon reflects the current state when returning to the fragment
        updateBluetoothIcon(BluetoothAdapter.getDefaultAdapter()?.isEnabled == true)
    }

    override fun onPause() {
        super.onPause()
        activity?.unregisterReceiver(bluetoothStateReceiver)
    }

    private fun setupUI() {
        // This is now handled directly in onResume, but keep your other setup logic here.
        bluetoothStatusImageView.setOnClickListener {
            showToast(if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true)
                "O Bluetooth do seu aparelho está ativado!"
            else "O Bluetooth do seu aparelho está desativado, por favor ative-o.")
        }
    }

    private fun updateBluetoothIcon(isEnabled: Boolean) {
        bluetoothStatusImageView.setImageResource(if (isEnabled) R.drawable.ic_bluetooth_on else R.drawable.ic_bluetooth_off)
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnToggle = view.findViewById(R.id.btn_toggle)
        btnFrio = view.findViewById(R.id.btn_frio)
        btnNeutro = view.findViewById(R.id.btn_neutro)
        btnQuente = view.findViewById(R.id.btn_quente)
        lampWhite = view.findViewById(R.id.lamp_white)
        lampYellow = view.findViewById(R.id.lamp_yellow)
        connectionStatusTextView = view.findViewById(R.id.connectionStatusTextView)

        viewModel.isConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                btnToggle.visibility = View.VISIBLE
                btnQuente.visibility = View.VISIBLE
                btnNeutro.visibility = View.VISIBLE
                btnFrio.visibility = View.VISIBLE
                lampWhite.visibility = View.VISIBLE
                lampYellow.visibility = View.VISIBLE
            } else {
                btnToggle.visibility = View.GONE
                btnQuente.visibility = View.GONE
                btnNeutro.visibility = View.GONE
                btnFrio.visibility = View.GONE
                lampWhite.visibility = View.GONE
                lampYellow.visibility = View.GONE
            }
        }

        viewModel.lampWhiteState.observe(viewLifecycleOwner) { isOn ->
            lampWhite.setImageResource(if (isOn) R.drawable.ic_frio else R.drawable.ic_desligado)
        }

        viewModel.lampYellowState.observe(viewLifecycleOwner) { isOn ->
            lampYellow.setImageResource(if (isOn) R.drawable.ic_quente else R.drawable.ic_desligado)
        }

        viewModel.connectedDevices.observe(viewLifecycleOwner) { devices ->
            val textToShow = when {
                devices.isEmpty() -> getString(R.string.no_device_connected)
                devices.size == 1 -> getString(R.string.connected_to, viewModel.getDeviceCustomName(devices.first().address, devices.first().name))
                else -> getString(R.string.devices_connected, devices.size)
            }
            connectionStatusTextView.text = textToShow
        }

        btnToggle.setOnClickListener {
            val stringToSend = "00"
            viewModel.sendCommandToAllDevices(stringToSend)  // Change this call
        }

        btnFrio.setOnClickListener {
            val stringToSend = "01"
            viewModel.sendCommandToAllDevices(stringToSend)  // Change this call
        }

        btnNeutro.setOnClickListener {
            val stringToSend = "11"
            viewModel.sendCommandToAllDevices(stringToSend)  // Change this call
        }

        btnQuente.setOnClickListener {
            val stringToSend = "10"
            viewModel.sendCommandToAllDevices(stringToSend)  // Change this call
        }
    }
}

