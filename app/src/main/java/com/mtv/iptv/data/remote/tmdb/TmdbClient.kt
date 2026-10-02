package com.mtv.iptv.data.remote.tmdb

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mtv.iptv.BuildConfig
import com.mtv.iptv.data.remote.HttpClientProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class TmdbClient(private val httpProvider: HttpClientProvider) {

    /** La key vive compilada en BuildConfig (inyectada en build.gradle.kts), no en el código. */
    val apiKey: String = BuildConfig.TMDB_API_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Se reconstruye solo cuando cambia el modo de DNS: el toggle de DNS
     * privado aplica de verdad sin pagar el costo en cada request.
     * (La sintaxis `client.api` sigue funcionando igual.)
     */
    private var cachedDnsMode: Boolean? = null
    private var cachedApi: TmdbApi? = null
    val api: TmdbApi
        get() {
            val mode = httpProvider.usePrivateDns
            if (cachedApi == null || cachedDnsMode != mode) {
                cachedDnsMode = mode
                cachedApi = Retrofit.Builder()
                    .baseUrl("https://api.themoviedb.org/3/")
                    .client(
                        httpProvider.client().newBuilder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build()
                    )
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(TmdbApi::class.java)
            }
            return cachedApi!!
        }

    companion object {
        fun posterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w500$it" }
        fun backdropUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
        fun profileUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w185$it" }
    }
}
