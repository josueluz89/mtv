package com.mtv.iptv.data.remote

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Proveedor central de OkHttpClient para todo el tráfico de red de la app
 * (Xtream, TMDB y test de velocidad).
 *
 * Soporta DNS privado: cuando [usePrivateDns] está activado, las resoluciones
 * DNS van por DNS-over-HTTPS contra Cloudflare (1.1.1.1); si no, se usa el DNS
 * del sistema. El cambio es REAL: el `dns()` se fija al construir cada cliente
 * y se mantienen dos instancias cacheadas (una por modo) para no reconstruir
 * en cada request. El flag es @Volatile así que el toggle aplica de inmediato
 * a los próximos requests (XtreamClient/TmdbClient piden el cliente en cada
 * llamada a `api()`).
 */
class HttpClientProvider {

    /** Lo actualiza el toggle de Configuración y MtvApplication al arrancar. */
    @Volatile
    var usePrivateDns: Boolean = false

    /** Cliente bootstrap solo para las consultas DoH (no necesita DNS del sistema). */
    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val doh: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            // IPs literales: no requieren resolución previa (rompe el huevo-gallina).
            .bootstrapDnsHosts(
                listOf(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                )
            )
            .build()
    }

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)

    private val systemClient: OkHttpClient by lazy { baseBuilder().dns(Dns.SYSTEM).build() }
    private val dohClient: OkHttpClient by lazy { baseBuilder().dns(doh).build() }

    /** Cliente según el ajuste actual de DNS privado. */
    fun client(): OkHttpClient = if (usePrivateDns) dohClient else systemClient

    /** Etiqueta para la UI: "Cloudflare 1.1.1.1" o "sistema". */
    fun dnsLabel(): String = if (usePrivateDns) "Cloudflare 1.1.1.1" else "sistema"
}
