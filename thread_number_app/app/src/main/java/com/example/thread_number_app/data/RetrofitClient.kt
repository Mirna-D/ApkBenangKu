package com.example.thread_number_app.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // GANTI sesuai IP server FastAPI-mu
    private const val BASE_URL = "https://carolann-subfrontal-earlie.ngrok-free.dev/"

    // OkHttp Logging (lihat request & response)
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)   // timeout koneksi ke server
            .readTimeout(60, TimeUnit.SECONDS)      // timeout baca respons server
            .writeTimeout(60, TimeUnit.SECONDS)     // timeout kirim data ke server
            .build()
    }

    // Retrofit Instance
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    // API SERVICE
    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
