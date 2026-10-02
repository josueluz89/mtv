package com.mtv.iptv.data.remote.tmdb

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mtv.iptv.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class TmdbClient {

    /** La key vive compilada en BuildConfig (inyectada en build.gradle.kts), no en el código. */
    val apiKey: String = BuildConfig.TMDB_API_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val api: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TmdbApi::class.java)

    companion object {
        fun posterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w500$it" }
        fun backdropUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
        fun profileUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w185$it" }
    }
}
