package com.mtv.iptv.data.remote.xtream

import com.mtv.iptv.data.local.db.ServerEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream

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

    /** Servidores Xtream ocultos (no se muestran en la UI). Se prueban en orden. */
    private val hiddenServers = listOf(
        "http://liontv.es:8080",
        "https://tvprem.pro:80",
        "http://cciptv.es:80",
    )

    @Volatile
    var session: XtreamSession? = null
        private set

    private val liveCategoriesCache = mutableListOf<XtreamCategory>()
    private val vodCategoriesCache = mutableListOf<XtreamCategory>()
    private val seriesCategoriesCache = mutableListOf<XtreamCategory>()

    /** Resultado de probar una URL candidata. */
    private sealed interface Attempt {
        data object Ok : Attempt
        /** El servidor respondió pero auth != 1 (o la respuesta no es un objeto). */
        data object Denied : Attempt
        /** Error de red/parseo al intentar la URL. */
        data class Failed(val error: Throwable) : Attempt
    }

    /**
     * Prueba un login contra una URL candidata ya construida.
     * Una respuesta que no es JsonObject se trata como credenciales inválidas,
     * no como error de red.
     */
    private suspend fun attempt(api: XtreamApi, username: String, password: String): Attempt {
        return try {
            val resp = api.login(username, password)
            val userInfo = (resp as? JsonObject)?.get("user_info") as? JsonObject
            val auth = (userInfo?.get("auth") as? JsonPrimitive)
                ?.content?.trim()?.toIntOrNull() ?: 0
            if (auth == 1) Attempt.Ok else Attempt.Denied
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Attempt.Failed(e)
        }
    }

    private fun buildSession(
        api: XtreamApi,
        baseUrl: String,
        username: String,
        password: String,
    ): XtreamSession {
        val s = XtreamSession(
            server = ServerEntity(
                name = "MTV",
                url = client.normalize(baseUrl).trimEnd('/'),
                username = username,
                password = password,
            ),
            baseUrl = client.normalize(baseUrl).trimEnd('/'),
            api = api,
            username = username,
            password = password,
        )
        session = s
        clearCache()
        return s
    }

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
            val api = client.api(candidate)
            when (val a = attempt(api, server.username, server.password)) {
                Attempt.Ok ->
                    return@withContext LoginResult.Ok(
                        buildSession(api, candidate, server.username, server.password)
                    )
                Attempt.Denied -> return@withContext LoginResult.AuthFailed
                is Attempt.Failed -> lastError = a.error
            }
        }
        LoginResult.NetworkError(lastError?.message ?: "Sin conexión con el servidor")
    }

    /**
     * Login con servidores ocultos: prueba cada URL en orden (cada una con su
     * fallback https <-> http). Si un servidor responde auth=0, sigue con el
     * siguiente (las credenciales pueden ser válidas en otro). Usa el primero
     * con auth=1. Si todos dan auth=0 → AuthFailed; si todos fallan por red →
     * NetworkError.
     */
    suspend fun loginAuto(username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            var sawDenied = false
            var lastError: Throwable? = null
            for (base in hiddenServers) {
                val candidates = listOfNotNull(base, client.alternateScheme(base)).distinct()
                for (candidate in candidates) {
                    val api = client.api(candidate)
                    when (val a = attempt(api, username, password)) {
                        Attempt.Ok ->
                            return@withContext LoginResult.Ok(
                                buildSession(api, candidate, username, password)
                            )
                        Attempt.Denied -> {
                            sawDenied = true
                            break // siguiente servidor, sin probar el esquema alterno
                        }
                        is Attempt.Failed -> lastError = a.error
                    }
                }
            }
            if (sawDenied) LoginResult.AuthFailed
            else LoginResult.NetworkError(lastError?.message ?: "Sin conexión con el servidor")
        }

    /**
     * Reemplaza la entidad de servidor de la sesión activa (para atarla a la
     * fila persistida en la base de datos y que los favoritos sigan funcionando).
     */
    fun updateSessionServer(server: ServerEntity) {
        session = session?.copy(server = server)
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
            liveCategoriesCache += fetchList("get_live_categories", XtreamCategory.serializer())
        }
        liveCategoriesCache.toList()
    }

    suspend fun getVodCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (vodCategoriesCache.isEmpty()) {
            vodCategoriesCache += fetchList("get_vod_categories", XtreamCategory.serializer())
        }
        vodCategoriesCache.toList()
    }

    suspend fun getSeriesCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (seriesCategoriesCache.isEmpty()) {
            seriesCategoriesCache += fetchList("get_series_categories", XtreamCategory.serializer())
        }
        seriesCategoriesCache.toList()
    }

    // ---------------- Contenido ----------------

    suspend fun getLiveStreams(categoryId: String? = null): List<XtreamLiveStream> =
        fetchList(
            "get_live_streams",
            XtreamLiveStream.serializer(),
            categoryId = categoryId?.takeIf { it.isNotBlank() },
        )

    suspend fun getVodStreams(categoryId: String? = null): List<XtreamVodStream> =
        fetchList(
            "get_vod_streams",
            XtreamVodStream.serializer(),
            categoryId = categoryId?.takeIf { it.isNotBlank() },
        )

    suspend fun getSeries(categoryId: String? = null): List<XtreamSeries> =
        fetchList(
            "get_series",
            XtreamSeries.serializer(),
            categoryId = categoryId?.takeIf { it.isNotBlank() },
        )

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

    /**
     * Descarga una lista Xtream decodificándola directo del socket, sin
     * bufferizar la respuesta completa en memoria.
     *
     * El conversor estándar de Retrofit primero convierte TODO el cuerpo a
     * JsonElement: con listas de decenas de miles de items (típico en estos
     * paneles) eso revienta el heap con OutOfMemoryError y la app se cierra
     * al cargar la lista. Aquí solo vive en memoria la lista final de DTOs.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> fetchList(
        action: String,
        serializer: KSerializer<T>,
        categoryId: String? = null,
        vodId: Int? = null,
        seriesId: Int? = null,
    ): List<T> = withContext(Dispatchers.IO) {
        val s = requireSession()
        try {
            s.api.actionStream(
                s.username, s.password, action,
                categoryId = categoryId,
                vodId = vodId,
                seriesId = seriesId,
            ).use { body ->
                body.byteStream().use { stream ->
                    client.json.decodeFromStream(ListSerializer(serializer), stream)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}
