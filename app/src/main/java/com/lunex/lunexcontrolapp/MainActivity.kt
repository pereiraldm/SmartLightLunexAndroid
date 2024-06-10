package com.lunex.lunexcontrolapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var temperatureText: TextView
    private lateinit var humidityText: TextView
    private lateinit var logoImageView: ImageView
    private lateinit var viewModel: DeviceViewModel

    private fun setupObservers() {
        val viewModel: DeviceViewModel by viewModels()

        viewModel.temperature.observe(this) { temperature ->
            temperatureText.text = temperature
        }

        viewModel.humidity.observe(this) { humidity ->
            humidityText.text = humidity
        }

        viewModel.connectedDevices.observe(this) { devices ->
            if (devices.size != 1) {
                temperatureText.text = "-- °C"
                humidityText.text = "-- %"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[DeviceViewModel::class.java]

        viewModel.scanForDevices() // This is an example. Adjust based on your app's flow.
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        bottomNav.post {
            val navController = findNavController(R.id.nav_host_fragment)
            bottomNav.setupWithNavController(navController)
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Incorporando o layout personalizado à Toolbar
        val customToolbarLayout = layoutInflater.inflate(R.layout.toolbar_layout, null)
        toolbar.addView(customToolbarLayout)

        temperatureText = customToolbarLayout.findViewById(R.id.temperature_text)
        humidityText = customToolbarLayout.findViewById(R.id.humidity_text)
        logoImageView = customToolbarLayout.findViewById(R.id.logo)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        logoImageView.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.lunex.com.br/"))
            startActivity(browserIntent)
        }

        // Dynamically set the IP address
        val newIpAddress = "10.0.0.98" // This should be dynamically retrieved from the ESP32's response
        viewModel.updateIpAddress(newIpAddress)

        // Optionally, trigger the viewModel to fetch device info
        // viewModel.getDeviceInfo()

        setupObservers()
        showWelcomeDialog()
    }

    private fun showWelcomeDialog() {
        val sharedPref = this.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val showDialog = sharedPref.getBoolean("ShowWelcomeDialog", true)

        if (showDialog) {
            val alertDialog = AlertDialog.Builder(this)
            alertDialog.setTitle("Bem-vindo!")
            alertDialog.setMessage("Assista ao vídeo do manual de uso.")
            alertDialog.setNegativeButton("Assistir") { _, _ ->
                // Abrir o link do vídeo
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/5czw-sAOq2U"))
                startActivity(intent)
            }
            alertDialog.setPositiveButton("Não mostrar novamente") { _, _ ->
                sharedPref.edit().putBoolean("ShowWelcomeDialog", false).apply()
            }
            alertDialog.show()
        }
    }
}