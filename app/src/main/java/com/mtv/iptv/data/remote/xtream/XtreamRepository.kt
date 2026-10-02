package com.mtv.iptv.data.remote.xtream

import com.mtv.iptv.data.local.db.ServerEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    companion object {
        /**
         * Tamaño de ventana por defecto de las APIs paginadas
         * ([getVodStreamsPaged], [getSeriesPaged], [getLiveStreamsPaged]).
         * El servidor Xtream no pagina del lado del servidor, así que las
         * ventanas se sirven del caché de catálogo de la sesión: la UI pide
         * de a ~200 en vez de retener/manipular los ~86k items de una vez.
         */
        const val PAGE_SIZE = 200
    }

    /** Servidores Xtream ocultos (no se muestran en la UI). Se prueban en orden. */
    private val hiddenServers = listOf(
        "http://liontv.es:8080",
        "https://tvprem.pro:80",
        "http://cciptv.es:80",
    )

    @Volatile
    var session: XtreamSession? = null
        private set

    /** Hook de diagnóstico (AppContainer lo conecta con CrashReporter). */
    var eventLog: ((String) -> Unit)? = null

    private val liveCategoriesCache = mutableListOf<XtreamCategory>()
    private val vodCategoriesCache = mutableListOf<XtreamCategory>()
    private val seriesCategoriesCache = mutableListOf<XtreamCategory>()

    /**
     * Catálogos completos por categoría ("all" = sin filtro), cacheados UNA vez
     * por sesión. Antes cada llamada a getVodStreams()/getSeries() descargaba
     * de nuevo decenas de miles de items; ahora la descarga ocurre una vez y
     * las ventanas paginadas y el índice de títulos se sirven de aquí.
     */
    private val vodStreamsCache = mutableMapOf<String, List<XtreamVodStream>>()
    private val seriesCache = mutableMapOf<String, List<XtreamSeries>>()
    private val liveStreamsCache = mutableMapOf<String, List<XtreamLiveStream>>()
    private val catalogMutex = Mutex()

    /**
     * Índice título-normalizado → item, construido UNA vez por sesión de
     * forma perezosa (en el primer findVodByTitle/findSeriesByTitle) a partir
     * del catálogo completo. Evita re-descargar el catálogo en cada tap.
     * Se invalida en logout/limpiar caché (ver [clearCache]).
     */
    private var vodTitleIndex: Map<String, XtreamVodStream>? = null
    private var seriesTitleIndex: Map<String, XtreamSeries>? = null

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
     * Login con servidores ocultos: prueba los 3 servidores EN PARALELO
     * (cada uno con su fallback https <-> http en serie), quedándose con el
     * primero que autentique (auth=1) y cancelando los demás.
     *
     * Semántica preservada del modo serie: si un servidor responde auth=0 se
     * marca como denegado y NO se prueba su esquema alterno (las credenciales
     * pueden ser válidas en otro servidor). Si todos dan auth=0 → AuthFailed;
     * si todos fallan por red → NetworkError.
     *
     * Antes tardaba ~72s en el peor caso (3 servidores × 2 esquemas en
     * serie); ahora el peor caso es el del servidor más lento.
     */
    suspend fun loginAuto(username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            var winner: LoginResult? = null
            var sawDenied = false
            var lastError: Throwable? = null
            supervisorScope {
                // Capacidad = nº de servidores: los send nunca suspenden.
                val results = Channel<AutoProbe>(capacity = hiddenServers.size)
                for (base in hiddenServers) {
                    launch { results.send(probeServer(base, username, password)) }
                }
                var remaining = hiddenServers.size
                while (remaining > 0 && winner == null) {
                    when (val r = results.receive()) {
                        is AutoProbe.Ok -> {
                            // Ganador: cancela los intentos que siguen en curso.
                            coroutineContext.cancelChildren()
                            winner = LoginResult.Ok(
                                buildSession(r.api, r.baseUrl, username, password)
                            )
                        }
                        AutoProbe.Denied -> sawDenied = true
                        is AutoProbe.Failed -> lastError = r.error
                    }
                    remaining--
                }
            }
            winner ?: if (sawDenied) LoginResult.AuthFailed
            else LoginResult.NetworkError(lastError?.message ?: "Sin conexión con el servidor")
        }

    /** Resultado de probar UN servidor oculto (con su fallback de esquema). */
    private sealed interface AutoProbe {
        data class Ok(val api: XtreamApi, val baseUrl: String) : AutoProbe
        data object Denied : AutoProbe
        data class Failed(val error: Throwable) : AutoProbe
    }

    /**
     * Prueba un servidor oculto: primero su URL base y, solo si falla por
     * red/timeout/SSL, el esquema alterno (https <-> http). Un auth=0 corta
     * aquí mismo (no prueba el esquema alterno).
     */
    private suspend fun probeServer(
        base: String,
        username: String,
        password: String,
    ): AutoProbe {
        var lastError: Throwable? = null
        val candidates = listOfNotNull(base, client.alternateScheme(base)).distinct()
        for (candidate in candidates) {
            val api = client.api(candidate)
            when (val a = attempt(api, username, password)) {
                Attempt.Ok -> return AutoProbe.Ok(api, candidate)
                Attempt.Denied -> return AutoProbe.Denied
                is Attempt.Failed -> lastError = a.error
            }
        }
        return AutoProbe.Failed(lastError ?: IllegalStateException("Sin conexión"))
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
        // Invalida catálogos e índices de la sesión (logout / cambio de servidor).
        vodStreamsCache.clear()
        seriesCache.clear()
        liveStreamsCache.clear()
        vodTitleIndex = null
        seriesTitleIndex = null
    }

    private fun cacheKey(categoryId: String?): String =
        categoryId?.takeIf { it.isNotBlank() } ?: "all"

    private fun requireSession(): XtreamSession =
        session ?: throw IllegalStateException("Sin sesión Xtream")

    // ---------------- Categorías ----------------

    suspend fun getLiveCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (liveCategoriesCache.isEmpty()) {
            liveCategoriesCache += fetchListOrEmpty("get_live_categories", XtreamCategory.serializer())
        }
        liveCategoriesCache.toList()
    }

    suspend fun getVodCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (vodCategoriesCache.isEmpty()) {
            vodCategoriesCache += fetchListOrEmpty("get_vod_categories", XtreamCategory.serializer())
        }
        vodCategoriesCache.toList()
    }

    suspend fun getSeriesCategories(): List<XtreamCategory> = withContext(Dispatchers.IO) {
        if (seriesCategoriesCache.isEmpty()) {
            seriesCategoriesCache += fetchListOrEmpty("get_series_categories", XtreamCategory.serializer())
        }
        seriesCategoriesCache.toList()
    }

    // ---------------- Contenido (cacheado por sesión) ----------------

    /**
     * Lista completa de streams en vivo de una categoría. Firma original
     * intacta para la UI; ahora se descarga una sola vez por sesión y las
     * llamadas siguientes se sirven del caché. Los fallos de red NO se
     * cachean: devuelven lista vacía (como antes) y el reintento de la UI
     * vuelve a descargar.
     */
    suspend fun getLiveStreams(categoryId: String? = null): List<XtreamLiveStream> {
        val key = cacheKey(categoryId)
        liveStreamsCache[key]?.let { return it }
        val list = try {
            fetchList(
                "get_live_streams",
                XtreamLiveStream.serializer(),
                categoryId = categoryId?.takeIf { it.isNotBlank() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        return catalogMutex.withLock { liveStreamsCache.getOrPut(key) { list } }
    }

    /**
     * Lista completa de películas de una categoría. Firma original intacta
     * para la UI; ahora se descarga una sola vez por sesión. Los fallos de
     * red NO se cachean (ver [getLiveStreams]).
     */
    suspend fun getVodStreams(categoryId: String? = null): List<XtreamVodStream> {
        val key = cacheKey(categoryId)
        vodStreamsCache[key]?.let { return it }
        val list = try {
            fetchList(
                "get_vod_streams",
                XtreamVodStream.serializer(),
                categoryId = categoryId?.takeIf { it.isNotBlank() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        return catalogMutex.withLock { vodStreamsCache.getOrPut(key) { list } }
    }

    /**
     * Lista completa de series de una categoría. Firma original intacta para
     * la UI; ahora se descarga una sola vez por sesión. Los fallos de red NO
     * se cachean (ver [getLiveStreams]).
     */
    suspend fun getSeries(categoryId: String? = null): List<XtreamSeries> {
        val key = cacheKey(categoryId)
        seriesCache[key]?.let { return it }
        val list = try {
            fetchList(
                "get_series",
                XtreamSeries.serializer(),
                categoryId = categoryId?.takeIf { it.isNotBlank() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        return catalogMutex.withLock { seriesCache.getOrPut(key) { list } }
    }

    // ---------------- Ventanas paginadas (NUEVO, para la UI) ----------------

    /**
     * Ventana del catálogo VOD: devuelve como máximo [limit] items desde
     * [offset], servidos del caché de la sesión (una sola descarga).
     * Pensada para pedir de a [PAGE_SIZE] (~200) en vez de los ~86k de una vez.
     */
    suspend fun getVodStreamsPaged(
        categoryId: String? = null,
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
    ): List<XtreamVodStream> = getVodStreams(categoryId).window(offset, limit)

    /** Total de items VOD (para dimensionar la paginación en la UI). */
    suspend fun getVodStreamsCount(categoryId: String? = null): Int =
        getVodStreams(categoryId).size

    /** Ventana del catálogo de series (ver [getVodStreamsPaged]). */
    suspend fun getSeriesPaged(
        categoryId: String? = null,
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
    ): List<XtreamSeries> = getSeries(categoryId).window(offset, limit)

    /** Total de items de series (para dimensionar la paginación en la UI). */
    suspend fun getSeriesCount(categoryId: String? = null): Int =
        getSeries(categoryId).size

    /** Ventana del catálogo en vivo (ver [getVodStreamsPaged]). */
    suspend fun getLiveStreamsPaged(
        categoryId: String? = null,
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
    ): List<XtreamLiveStream> = getLiveStreams(categoryId).window(offset, limit)

    /** Total de items en vivo (para dimensionar la paginación en la UI). */
    suspend fun getLiveStreamsCount(categoryId: String? = null): Int =
        getLiveStreams(categoryId).size

    private fun <T> List<T>.window(offset: Int, limit: Int): List<T> {
        val from = offset.coerceAtLeast(0)
        if (from >= size || limit <= 0) return emptyList()
        return subList(from, (from + limit).coerceAtMost(size))
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
        // O(1) sobre el índice de la sesión: ya no re-descarga el catálogo en cada tap.
        vodTitleIndex()[cleanTitle.lowercase()]
    }

    /** Busca una serie por título limpio (para enlazar filmografías de TMDB con el servidor). */
    suspend fun findSeriesByTitle(cleanTitle: String): XtreamSeries? = withContext(Dispatchers.IO) {
        // O(1) sobre el índice de la sesión: ya no re-descarga el catálogo en cada tap.
        seriesTitleIndex()[cleanTitle.lowercase()]
    }

    /**
     * Índice título-normalizado → VOD de la sesión. Se construye de forma
     * perezosa en la primera búsqueda, a partir del catálogo completo
     * (que a su vez se descarga una sola vez por sesión). La clave es
     * exactamente la misma normalización que usaba la búsqueda lineal
     * (TitleCleaner + lowercase); ante duplicados gana el primer item,
     * igual que el firstOrNull anterior.
     */
    private suspend fun vodTitleIndex(): Map<String, XtreamVodStream> {
        vodTitleIndex?.let { return it }
        val all = getVodStreams(null)
        return catalogMutex.withLock {
            vodTitleIndex ?: buildTitleIndex(all) {
                com.mtv.iptv.data.remote.tmdb.TitleCleaner.clean(it.name).title.lowercase()
            }.also { vodTitleIndex = it }
        }
    }

    /** Índice título-normalizado → serie (ver [vodTitleIndex]). */
    private suspend fun seriesTitleIndex(): Map<String, XtreamSeries> {
        seriesTitleIndex?.let { return it }
        val all = getSeries(null)
        return catalogMutex.withLock {
            seriesTitleIndex ?: buildTitleIndex(all) {
                com.mtv.iptv.data.remote.tmdb.TitleCleaner.clean(it.name).title.lowercase()
            }.also { seriesTitleIndex = it }
        }
    }

    private fun <T> buildTitleIndex(
        items: List<T>,
        keyOf: (T) -> String,
    ): Map<String, T> {
        val map = LinkedHashMap<String, T>(items.size)
        for (item in items) {
            map.putIfAbsent(keyOf(item), item) // primer duplicado gana (como firstOrNull)
        }
        return map
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

    // ---------------- Buscador ----------------

    /**
     * Busca canales en vivo por nombre. Usa el catálogo de la sesión (una sola
     * descarga) y filtra en memoria. Query vacío o de 1 letra → lista vacía.
     */
    suspend fun searchLive(query: String): List<XtreamLiveStream> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.length < 2) return@withContext emptyList()
        getLiveStreams(null).filter { it.name.lowercase().contains(q) }.take(50)
    }

    /** Igual que [searchLive] pero sobre el catálogo de películas. */
    suspend fun searchVod(query: String): List<XtreamVodStream> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.length < 2) return@withContext emptyList()
        getVodStreams(null).filter { it.name.lowercase().contains(q) }.take(50)
    }

    /** Igual que [searchLive] pero sobre el catálogo de series. */
    suspend fun searchSeries(query: String): List<XtreamSeries> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.length < 2) return@withContext emptyList()
        getSeries(null).filter { it.name.lowercase().contains(q) }.take(50)
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
     *
     * Propaga las excepciones de red/parseo: cada llamador decide si las
     * traga (categorías y catálogos devuelven lista vacía SIN cachear el
     * fallo, para que el reintento de la UI re-descargue).
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
                eventLog?.invoke("$action: contentLength=${body.contentLength()}")
                body.byteStream().use { stream ->
                    val list = client.json.decodeFromStream(ListSerializer(serializer), stream)
                    eventLog?.invoke("$action: OK ${list.size} items")
                    list
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            eventLog?.invoke("$action: ERROR ${e::class.java.simpleName}: ${e.message}")
            throw e
        }
    }

    /**
     * Variante que traga los errores de red/parseo y devuelve lista vacía
     * (para las categorías, que reintentan solas mientras su caché siga vacío).
     */
    private suspend fun <T> fetchListOrEmpty(
        action: String,
        serializer: KSerializer<T>,
    ): List<T> = try {
        fetchList(action, serializer)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
}
