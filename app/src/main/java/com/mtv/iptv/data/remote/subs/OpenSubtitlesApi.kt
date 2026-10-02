package com.mtv.iptv.data.remote.subs

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mtv.iptv.data.remote.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente de OpenSubtitles (https://api.opensubtitles.com/api/v1).
 *
 * La búsqueda es por TMDB ID (no por nombre), como pidió el dueño:
 * - Película: tmdb_id de la película.
 * - Serie: tmdb_id de la serie + season_number + episode_number.
 *
 * Requiere API key gratuita (opensubtitles.com); se guarda en UserPrefs
 * ("opensubtitles_key") y se pide por parámetro en cada llamada.
 */
class OpenSubtitlesClient(private val httpProvider: HttpClientProvider) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Se reconstruye solo cuando cambia el modo de DNS (mismo patrón que TmdbClient).
    private var cachedDnsMode: Boolean? = null
    private var cachedApi: OpenSubtitlesApi? = null
    private val api: OpenSubtitlesApi
        get() {
            val mode = httpProvider.usePrivateDns
            if (cachedApi == null || cachedDnsMode != mode) {
                cachedDnsMode = mode
                cachedApi = Retrofit.Builder()
                    .baseUrl("https://api.opensubtitles.com/api/v1/")
                    .client(
                        httpProvider.client().newBuilder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build()
                    )
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(OpenSubtitlesApi::class.java)
            }
            return cachedApi!!
        }

    /**
     * Busca subtítulos por TMDB ID. Ordenados por descargas (los más usados primero).
     * @throws IllegalArgumentException si la key está vacía.
     */
    suspend fun searchSubtitles(
        apiKey: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        languages: String = "es",
    ): List<SubtitleResult> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Falta la API key de OpenSubtitles." }
        val res = api.search(apiKey, tmdbId, languages, season, episode)
        res.data.flatMap { item ->
            val a = item.attributes
            a.files.map { f ->
                SubtitleResult(
                    fileId = f.fileId,
                    fileName = f.fileName.ifBlank { a.release.ifBlank { "Subtítulo" } },
                    language = a.language.ifBlank { languages },
                    release = a.release,
                    downloadCount = a.downloadCount,
                )
            }
        }.filter { it.fileId > 0 }
            .sortedByDescending { it.downloadCount }
    }

    /**
     * Descarga el SRT del subtítulo elegido al caché de la app.
     * Pide el enlace temporal (POST /download) y luego baja los bytes con OkHttp.
     * @return el archivo .srt guardado en <cacheDir>/subtitulos/.
     */
    suspend fun downloadSubtitle(apiKey: String, fileId: Int, cacheDir: File): File =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Falta la API key de OpenSubtitles." }
            val link = api.download(apiKey, OsDownloadRequest(fileId)).link
            require(link.isNotBlank()) { "OpenSubtitles no devolvió enlace de descarga." }
            val req = Request.Builder().url(link).header("User-Agent", USER_AGENT).build()
            httpProvider.client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Descarga fallida (HTTP ${resp.code}).")
                }
                val body = resp.body ?: throw IOException("Descarga vacía.")
                val dir = File(cacheDir, "subtitulos").apply { mkdirs() }
                val out = File(dir, "mtv_sub_$fileId.srt")
                body.byteStream().use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                // Validación ligera: un SRT real trae líneas de tiempo "-->".
                val head = out.inputStream().use { stream ->
                    val buf = ByteArray(2048)
                    val n = stream.read(buf)
                    if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
                }
                if (!head.contains("-->")) {
                    out.delete()
                    throw IOException("El archivo descargado no es un subtítulo válido.")
                }
                out
            }
        }

    companion object {
        const val USER_AGENT = "MTV-IPTV/1.4"
    }
}

/** Resultado de búsqueda listo para la UI. */
data class SubtitleResult(
    val fileId: Int,
    val fileName: String,
    val language: String,
    val release: String,
    val downloadCount: Int,
)

internal interface OpenSubtitlesApi {

    @Headers("User-Agent: MTV-IPTV/1.4")
    @GET("subtitles")
    suspend fun search(
        @Header("Api-Key") apiKey: String,
        @Query("tmdb_id") tmdbId: Int,
        @Query("languages") languages: String,
        @Query("season_number") season: Int? = null,
        @Query("episode_number") episode: Int? = null,
    ): OsSubtitlesResponse

    @Headers("User-Agent: MTV-IPTV/1.4")
    @POST("download")
    suspend fun download(
        @Header("Api-Key") apiKey: String,
        @Body body: OsDownloadRequest,
    ): OsDownloadResponse
}

@kotlinx.serialization.Serializable
data class OsSubtitlesResponse(
    val data: List<OsSubtitleItem> = emptyList(),
)

@kotlinx.serialization.Serializable
data class OsSubtitleItem(
    val id: String = "",
    val attributes: OsSubtitleAttributes = OsSubtitleAttributes(),
)

@kotlinx.serialization.Serializable
data class OsSubtitleAttributes(
    val language: String = "",
    val release: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
    val files: List<OsSubtitleFile> = emptyList(),
)

@kotlinx.serialization.Serializable
data class OsSubtitleFile(
    @SerialName("file_id") val fileId: Int = 0,
    @SerialName("file_name") val fileName: String = "",
)

@kotlinx.serialization.Serializable
data class OsDownloadRequest(
    @SerialName("file_id") val fileId: Int,
)

@kotlinx.serialization.Serializable
data class OsDownloadResponse(
    val link: String = "",
)
