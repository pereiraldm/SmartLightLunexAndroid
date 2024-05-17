package com.lunex.lunexcontrolapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels


class AvancadoFragment : Fragment() {
    private lateinit var seekBar: SeekBar
    private lateinit var seekBarValue: TextView
    lateinit var advancedToggleButton: SwitchCompat
    private lateinit var connected_devices_advanced: TextView
    private lateinit var lampYellow: ImageView
    private lateinit var lampWhite: ImageView
    private lateinit var linearBlock: LinearLayout
    private lateinit var seekBarBrightness: SeekBar
    private lateinit var brightnessValue: TextView
    private val viewModel: BluetoothViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_avancado, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        linearBlock = view.findViewById(R.id.LinearBlock)
        seekBar = view.findViewById(R.id.seekBar)
        seekBarValue = view.findViewById(R.id.seekBarValue)
        connected_devices_advanced = view.findViewById(R.id.connected_devices_advanced)
        lampYellow = view.findViewById(R.id.lamp_yellow)
        lampWhite = view.findViewById(R.id.lamp_white)
        seekBarBrightness = view.findViewById(R.id.seekBarBrightness)
        brightnessValue = view.findViewById(R.id.brightnessValue) // Assume you have a TextView for this
        advancedToggleButton = view.findViewById(R.id.advancedToggleButton) // Ensure this ID matches your layout
        val switchDescription: TextView = view.findViewById(R.id.switchDescription)

        val sharedPref = activity?.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPref?.getBoolean("AdvancedToggleState", false) ?: false

        val isAdvancedToggleChecked = sharedPref?.getBoolean("AdvancedToggleState", false) ?: false
        advancedToggleButton.isChecked = isAdvancedToggleChecked

        val savedSeekBarValue = sharedPref?.getInt("SeekBarValue", 0) ?: 0

        switchDescription.text = if (advancedToggleButton.isChecked) "DESATIVAR" else "ATIVAR"

        seekBar.progress = savedSeekBarValue
        advancedToggleButton.setOnCheckedChangeListener(null)  // Temporariamente remover o listener
        advancedToggleButton.isChecked = false
        seekBar.isEnabled = false
        seekBarBrightness.isEnabled = false
        advancedToggleButton.setOnCheckedChangeListener { _, isChecked ->
            switchDescription.text = if (isChecked) "DESATIVAR" else "ATIVAR"
            seekBar.isEnabled = isChecked
            seekBarBrightness.isEnabled = isChecked
            val command = if (isChecked) "avancado" else "desligarAvancado"
            viewModel.sendCommandToAllDevices(command)  // Change this call
            if (isChecked) {
                // Adding a slight delay to ensure the command is processed by the device
                view.postDelayed({
                    sendSeekBarValueToDevice(seekBar.progress)
                }, 500) // Adjust delay as needed based on device response time
            }
        }

        seekBar.max = 3500   // Ajuste o máximo do SeekBar para 3500 (6500 - 3000)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val adjustedProgress = progress + 3000  // Ajuste o valor para o intervalo 3000-6500
                seekBarValue.text = "COR: $adjustedProgress (3000 - 6500)"


                viewModel.lampWhiteState.observe(viewLifecycleOwner) { isOn ->
                    lampWhite.setImageResource(if (isOn) R.drawable.ic_frio else R.drawable.ic_desligado)
                }

                viewModel.lampYellowState.observe(viewLifecycleOwner) { isOn ->
                    lampYellow.setImageResource(if (isOn) R.drawable.ic_quente else R.drawable.ic_desligado)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Nada a fazer aqui
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let {
                    sendSeekBarValueToDevice(it)
                }
            }
        })

        // Set up listener for the brightness SeekBar
        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessValue.text = "INTENSIDADE: $progress%"

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Optional: Implement if needed
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Optional: Implement if needed
                seekBarBrightness.progress.let {
                    sendBrightnessValueToDevice(it)
                }
            }
        })

        viewModel.isConnected.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                advancedToggleButton.visibility = View.VISIBLE
                seekBar.visibility = View.VISIBLE
                lampWhite.visibility = View.VISIBLE
                lampYellow.visibility = View.VISIBLE
                switchDescription.visibility = View.VISIBLE
                seekBarValue.visibility = View.VISIBLE
                linearBlock.visibility = View.VISIBLE
                seekBarBrightness.visibility = View.VISIBLE
                brightnessValue.visibility = View.VISIBLE
                connected_devices_advanced.visibility = View.VISIBLE
            } else {
                advancedToggleButton.isChecked = false // Reset the toggle button state
                advancedToggleButton.visibility = View.GONE
                seekBar.visibility = View.GONE
                lampWhite.visibility = View.GONE
                lampYellow.visibility = View.GONE
                switchDescription.visibility = View.GONE
                seekBarValue.visibility = View.GONE
                linearBlock.visibility = View.GONE
                seekBarBrightness.visibility = View.GONE
                brightnessValue.visibility = View.GONE
                connected_devices_advanced.visibility = View.VISIBLE
                connected_devices_advanced.text = getString(R.string.no_device_connected)
            }
        }

        viewModel.connectedDevices.observe(viewLifecycleOwner) { devices ->
            @SuppressLint("MissingPermission")
            val textToShow = when {
                devices.isEmpty() -> getString(R.string.no_device_connected)
                devices.size == 1 -> getString(R.string.connected_to, viewModel.getDeviceCustomName(devices.first().address, devices.first().name))
                else -> getString(R.string.devices_connected, devices.size)
            }
            connected_devices_advanced.text = textToShow
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        advancedToggleButton.isChecked = false // Reset the toggle button state
        val sharedPref = activity?.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPref?.edit()?.remove("AdvancedToggleState")?.apply()
    }

    override fun onStop() {
        super.onStop()
        advancedToggleButton.isChecked = false // Ensure this is the correct ID
        // Optionally, save the state to SharedPreferences if needed
        val sharedPref = activity?.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPref?.edit()?.putBoolean("AdvancedToggleState", false)?.apply()
    }

    private fun scaleSeekBarValue(userValue: Int): Int {
        // Scale from user-visible range (3000-6500) to device range (0-1024)
        return ((userValue - 3000) / (6500.0 - 3000) * 1024).toInt()
    }

    private fun sendSeekBarValueToDevice(progress: Int) {
        val userValue = progress + 3000 // Convert progress back to user range
        val scaledValue = scaleSeekBarValue(userValue)
        viewModel.sendCommandToAllDevices(scaledValue.toString())  // Change this call
    }

    private fun sendBrightnessValueToDevice(brightness: Int) {
        // Convert brightness to string and send to device
        viewModel.sendCommandToAllDevices("INTENSIDADE:$brightness")  // Change this call
        Log.d("AvancadoFragment", "Sending Brightness value: $brightness")
    }
    companion object {
        const val DEBUG = false // Mude para 'true' durante o desenvolvimento, 'false' para produção
    }
}