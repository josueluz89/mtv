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
)

class TmdbRepository(private val client: TmdbClient) {

    private val movieCache = mutableMapOf<String, TmdbMedia?>()
    private val seriesCache = mutableMapOf<String, TmdbMedia?>()

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
            )
        } catch (e: Exception) {
            null
        }
        seriesCache[key] = result
        result
    }
}
