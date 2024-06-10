package com.lunex.lunexcontrolapp

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SharedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ESP_PREFS", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveDevices(devices: List<ESPDevice>) {
        val json = gson.toJson(devices)
        prefs.edit().putString("devices", json).apply()
    }

    fun getDevices(): List<ESPDevice> {
        val json = prefs.getString("devices", null)
        if (json != null) {
            val type = object : TypeToken<List<ESPDevice>>() {}.type
            return gson.fromJson(json, type)
        }
        return emptyList()
    }
}
