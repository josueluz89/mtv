package com.mtv.iptv.data.remote.xtream

import com.mtv.iptv.data.local.db.ServerEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class XtreamSession(
    val server: ServerEntity,
    /** Base URL efectiva (sin trailing slash), tras aplicar el fallback de esquema. */
    val baseUrl: String,
    val api: XtreamApi,
    val username: String,
    val password: String,
)

sealed interface LoginResult {
    data class Ok(val session: XtreamSession) : LoginResult
    data object AuthFailed : LoginResult
    data class NetworkError(val message: String) : LoginResult
}

class XtreamRepository(private val client: XtreamClient) {

    @Volatile
    var session: XtreamSession? = null
        private set

    private val liveCategoriesCache = mutableListOf<XtreamCategory>()
    private val vodCategoriesCache = mutableListOf<XtreamCategory>()
    private val seriesCategoriesCache = mutableListOf<XtreamCategory>()

    /**
     * Login Xtream (valida user_info.auth == 1).
     *
     * Si la conexión con el esquema original falla por red/timeout/SSL, reintenta
     * automáticamente con el esquema alterno (https <-> http) antes de reportar
     * error. Las credenciales inválidas (auth == 0) no reintentan.
     */
    suspend fun login(server: ServerEntity): LoginResult = withContext(Dispatchers.IO) {
        val candidates = listOfNotNull(server.url, client.alternateScheme(server.url)).distinct()
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val api = client.api(candidate)
                val resp = api.login(server.username, server.password)
                val auth = resp.jsonObject["user_info"]?.jsonObject
                    ?.get("auth")?.jsonPrimitive?.content?.trim()?.toIntOrNull() ?: 0
                if (auth == 1) {
                    val s = XtreamSession(
                        server = server,
                        baseUrl = client.normalize(candidate).trimEnd('/'),
                        api = api,
                        username = server.username,
                        password = server.password,
                    )
                    session = s
                    clearCache()
                    return@withContext LoginResult.Ok(s)
                }
                return@withContext LoginResult.AuthFailed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        LoginResult.NetworkError(lastError?.message ?: "Sin conexión con el servidor")
    }

    fun logout() {
        session = null
        clearCache()
    }

    private fun clearCache() {
        liveCategoriesCache.clear()
        vodCategoriesCache.clear()
        seriesCategoriesCache.clear()
    }

    private fun requireSession(): XtreamSession =
        session ?: throw IllegalStateException("Sin sesión Xtream")

    // ---------------- Categorías ----------------

    suspend fun getLiveCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (liveCategoriesCache.isEmpty()) {
            val s = requireSession()
            liveCategoriesCache += decodeList(
                s.api.action(s.username, s.password, "get_live_categories"),
                XtreamCategory.serializer(),
            )
        }
        liveCategoriesCache.toList()
    }

    suspend fun getVodCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (vodCategoriesCache.isEmpty()) {
            val s = requireSession()
            vodCategoriesCache += decodeList(
                s.api.action(s.username, s.password, "get_vod_categories"),
                XtreamCategory.serializer(),
            )
        }
        vodCategoriesCache.toList()
    }

    suspend fun getSeriesCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (seriesCategoriesCache.isEmpty()) {
            val s = requireSession()
            seriesCategoriesCache += decodeList(
                s.api.action(s.username, s.password, "get_series_categories"),
                XtreamCategory.serializer(),
            )
        }
        seriesCategoriesCache.toList()
    }

    // ---------------- Contenido ----------------

    suspend fun getLiveStreams(categoryId: String? = null): List<XtreamLiveStream> =
        withContext(Dispatchers.IO) {
            val s = requireSession()
            decodeList(
                s.api.action(s.username, s.password, "get_live_streams", categoryId = categoryId?.takeIf { it.isNotBlank() }),
                XtreamLiveStream.serializer(),
            )
        }

    suspend fun getVodStreams(categoryId: String? = null): List<XtreamVodStream> =
        withContext(Dispatchers.IO) {
            val s = requireSession()
            decodeList(
                s.api.action(s.username, s.password, "get_vod_streams", categoryId = categoryId?.takeIf { it.isNotBlank() }),
                XtreamVodStream.serializer(),
            )
        }

    suspend fun getSeries(categoryId: String? = null): List<XtreamSeries> =
        withContext(Dispatchers.IO) {
            val s = requireSession()
            decodeList(
                s.api.action(s.username, s.password, "get_series", categoryId = categoryId?.takeIf { it.isNotBlank() }),
                XtreamSeries.serializer(),
            )
        }

    suspend fun getVodInfo(vodId: Int): VodInfoResponse = withContext(Dispatchers.IO) {
        val s = requireSession()
        val el = s.api.action(s.username, s.password, "get_vod_info", vodId = vodId)
        if (el is JsonObject) client.json.decodeFromJsonElement(VodInfoResponse.serializer(), el)
        else VodInfoResponse()
    }

    suspend fun getSeriesInfo(seriesId: Int): SeriesInfoResponse = withContext(Dispatchers.IO) {
        val s = requireSession()
        val el = s.api.action(s.username, s.password, "get_series_info", seriesId = seriesId)
        if (el is JsonObject) client.json.decodeFromJsonElement(SeriesInfoResponse.serializer(), el)
        else SeriesInfoResponse()
    }

    /** Busca una película por título limpio (para enlazar "similares" de TMDB con el servidor). */
    suspend fun findVodByTitle(cleanTitle: String): XtreamVodStream? = withContext(Dispatchers.IO) {
        val all = getVodStreams(null)
        val target = cleanTitle.lowercase()
        all.firstOrNull { com.mtv.iptv.data.remote.tmdb.TitleCleaner.clean(it.name).title.lowercase() == target }
    }

    // ---------------- URLs de reproducción ----------------

    fun liveUrl(streamId: Int): String {
        val s = requireSession()
        return "${s.baseUrl}/live/${s.username}/${s.password}/$streamId.ts"
    }

    fun vodUrl(streamId: Int, containerExtension: String): String {
        val s = requireSession()
        val ext = containerExtension.ifBlank { "mp4" }
        return "${s.baseUrl}/movie/${s.username}/${s.password}/$streamId.$ext"
    }

    fun episodeUrl(episodeId: String, containerExtension: String): String {
        val s = requireSession()
        val ext = containerExtension.ifBlank { "mp4" }
        return "${s.baseUrl}/series/${s.username}/${s.password}/$episodeId.$ext"
    }

    // ---------------- Util ----------------

    private fun <T> decodeList(
        element: kotlinx.serialization.json.JsonElement,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> {
        if (element !is JsonArray) return emptyList()
        return client.json.decodeFromJsonElement(ListSerializer(serializer), element)
    }
}
