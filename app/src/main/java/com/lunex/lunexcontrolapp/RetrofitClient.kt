package com.lunex.lunexcontrolapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var retrofit: Retrofit? = null

    fun getClient(baseUrl: String): Retrofit {
        val url = if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            "http://$baseUrl"
        } else {
            baseUrl
        }

        if (retrofit == null || retrofit?.baseUrl().toString() != url) {
            retrofit = Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }
}


