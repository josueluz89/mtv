package com.mtv.iptv.data.remote.xtream

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mtv.iptv.data.remote.HttpClientProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class XtreamClient(private val httpProvider: HttpClientProvider) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * El cliente se pide al proveedor en cada llamada: así el toggle de DNS
     * privado aplica de verdad sin reconstruir nada más.
     */
    private val http: OkHttpClient
        get() = httpProvider.client()

    fun api(baseUrl: String): XtreamApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(normalize(baseUrl))
            .client(http)
            // Algunos paneles responden JSON con Content-Type: text/html
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addConverterFactory(json.asConverterFactory("text/html".toMediaType()))
            .build()
        return retrofit.create(XtreamApi::class.java)
    }

    /** Asegura esquema y trailing slash (Retrofit lo exige). */
    fun normalize(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        u = u.trimEnd('/')
        return "$u/"
    }

    /** Esquema alterno para el fallback https <-> http. */
    fun alternateScheme(url: String): String? {
        val t = url.trim()
        return when {
            t.startsWith("https://") -> "http://" + t.removePrefix("https://")
            t.startsWith("http://") -> "https://" + t.removePrefix("http://")
            else -> null
        }
    }
}
