package com.lunex.lunexcontrolapp

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @FormUrlEncoded
    @POST("/connect")
    fun connectToWiFi(
        @Field("ssid") ssid: String,
        @Field("password") password: String
    ): Call<Void>

    @FormUrlEncoded
    @POST("/command")
    fun sendCommand(
        @Field("command") command: String
    ): Call<Void>

    @GET("/info")
    fun getDeviceInfo(): Call<DeviceInfo>

    @GET("/heartbeat")
    fun heartbeat(): Call<String>
}

data class DeviceInfo(
    val id: String,
    val ip: String // Add this field to include the IP address
)



