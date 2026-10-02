package com.mtv.iptv.data.remote.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Metadata unificada de TMDB para películas y series. */
data class TmdbMedia(
    val title: String,
    val year: Int?,
    val overview: String,
    val rating: Double,
    val posterUrl: String?,
    val backdropUrl: String?,
    val cast: List<TmdbCastMember>,
    val similar: List<TmdbSearchResult>,
    val trailerKey: String?,
    val companies: List<TmdbProductionCompany> = emptyList(),
    /** ID numérico de TMDB (para buscar subtítulos por ID en OpenSubtitles). */
    val tmdbId: Int? = null,
)

/** Detalle de una persona (actor/actriz) + su filmografía ordenada por rating. */
data class PersonDetail(
    val details: TmdbPersonDetails,
    val filmography: List<TmdbCreditItem>,
)

/** Detalle de una productora + su catálogo (películas y series por separado). */
data class CompanyDetail(
    val details: TmdbCompanyDetails,
    val movies: List<TmdbSearchResult>,
    val series: List<TmdbSearchResult>,
)

class TmdbRepository(private val client: TmdbClient) {

    private val movieCache = mutableMapOf<String, TmdbMedia?>()
    private val seriesCache = mutableMapOf<String, TmdbMedia?>()

    private var trendingCache: List<TmdbSearchResult>? = null
    private var trendingCacheAt: Long = 0

    /**
     * Tendencias de la semana (películas + series con backdrop).
     * Cache en memoria de 6 h para no golpear TMDB en cada visita al inicio.
     */
    suspend fun trendingWeek(): List<TmdbSearchResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = trendingCache
        if (cached != null && now - trendingCacheAt < 6 * 60 * 60 * 1000L) return@withContext cached
        val fresh = try {
            client.api.trendingWeek(client.apiKey).results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .filter { !it.backdropPath.isNullOrBlank() }
                .take(10)
        } catch (e: Exception) {
            cached ?: emptyList()
        }
        trendingCache = fresh
        trendingCacheAt = now
        fresh
    }

    suspend fun findMovie(rawTitle: String, yearHint: Int? = null): TmdbMedia? =
        withContext(Dispatchers.IO) {
            val cleaned = TitleCleaner.clean(rawTitle)
            val key = "m:${cleaned.title.lowercase()}:${cleaned.year ?: yearHint}"
            if (movieCache.containsKey(key)) return@withContext movieCache[key]
            val result = try {
                val query = cleaned.title.ifBlank { return@withContext null }
                val hit = client.api.searchMovie(client.apiKey, query, cleaned.year ?: yearHint)
                    .results.firstOrNull() ?: return@withContext null
                val d = client.api.movieDetails(hit.id, client.apiKey)
                TmdbMedia(
                    title = d.title.ifBlank { query },
                    year = TitleCleaner.yearFromDate(d.releaseDate),
                    overview = d.overview,
                    rating = d.voteAverage,
                    posterUrl = TmdbClient.posterUrl(d.posterPath),
                    backdropUrl = TmdbClient.backdropUrl(d.backdropPath),
                    cast = d.credits.cast.take(15),
                    similar = (d.similar.results + d.recommendations.results)
                        .distinctBy { it.id }.take(15),
                    trailerKey = d.videos.results
                        .firstOrNull { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }?.key
                        ?: d.videos.results.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key,
                    companies = d.productionCompanies,
                    tmdbId = hit.id,
                )
            } catch (e: Exception) {
                null
            }
            movieCache[key] = result
            result
        }

    suspend fun findSeries(rawTitle: String): TmdbMedia? = withContext(Dispatchers.IO) {
        val cleaned = TitleCleaner.clean(rawTitle)
        val key = "s:${cleaned.title.lowercase()}"
        if (seriesCache.containsKey(key)) return@withContext seriesCache[key]
        val result = try {
            val query = cleaned.title.ifBlank { return@withContext null }
            val hit = client.api.searchTv(client.apiKey, query)
                .results.firstOrNull() ?: return@withContext null
            val d = client.api.tvDetails(hit.id, client.apiKey)
            TmdbMedia(
                title = d.name.ifBlank { query },
                year = TitleCleaner.yearFromDate(d.firstAirDate),
                overview = d.overview,
                rating = d.voteAverage,
                posterUrl = TmdbClient.posterUrl(d.posterPath),
                backdropUrl = TmdbClient.backdropUrl(d.backdropPath),
                cast = d.credits.cast.take(15),
                similar = (d.similar.results + d.recommendations.results)
                    .distinctBy { it.id }.take(15),
                trailerKey = d.videos.results
                    .firstOrNull { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }?.key
                    ?: d.videos.results.firstOrNull { it.site.equals("YouTube", ignoreCase = true) }?.key,
                companies = d.productionCompanies,
                tmdbId = hit.id,
            )
        } catch (e: Exception) {
            null
        }
        seriesCache[key] = result
        result
    }

    /** Detalle de una persona + filmografía (ordenada por rating, sin duplicados). */
    suspend fun getPerson(personId: Int): PersonDetail? = withContext(Dispatchers.IO) {
        try {
            val d = client.api.personDetails(personId, client.apiKey)
            val filmography = d.combinedCredits.cast
                .filter { it.displayTitle().isNotBlank() }
                .distinctBy { it.id }
                .sortedByDescending { it.voteAverage }
                .take(30)
            PersonDetail(d, filmography)
        } catch (e: Exception) {
            null
        }
    }

    /** Detalle de una productora + catálogo (discover películas y series). */
    suspend fun getCompany(companyId: Int): CompanyDetail? = withContext(Dispatchers.IO) {
        try {
            val d = client.api.companyDetails(companyId, client.apiKey)
            val movies = client.api.discoverMovie(client.apiKey, companyId)
                .results.distinctBy { it.id }.take(30)
            val series = client.api.discoverTv(client.apiKey, companyId)
                .results.distinctBy { it.id }.take(30)
            CompanyDetail(d, movies, series)
        } catch (e: Exception) {
            null
        }
    }
}
