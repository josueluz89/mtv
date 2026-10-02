package com.mtv.iptv.data.remote.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResponse(val results: List<TmdbSearchResult> = emptyList())

@Serializable
data class TmdbSearchResult(
    val id: Int = 0,
    val title: String = "",
    val name: String = "",
    /** "movie" | "tv" (solo viene en trending/all; default "" = desconocido). */
    @SerialName("media_type") val mediaType: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val overview: String = "",
)

@Serializable
data class TmdbMovieDetails(
    val id: Int = 0,
    val title: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val runtime: Int = 0,
    val credits: TmdbCredits = TmdbCredits(),
    val similar: TmdbPagedResults = TmdbPagedResults(),
    val videos: TmdbVideos = TmdbVideos(),
    val recommendations: TmdbPagedResults = TmdbPagedResults(),
    @SerialName("production_companies") val productionCompanies: List<TmdbProductionCompany> = emptyList(),
)

@Serializable
data class TmdbTvDetails(
    val id: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val credits: TmdbCredits = TmdbCredits(),
    val similar: TmdbPagedResults = TmdbPagedResults(),
    val videos: TmdbVideos = TmdbVideos(),
    val recommendations: TmdbPagedResults = TmdbPagedResults(),
    @SerialName("production_companies") val productionCompanies: List<TmdbProductionCompany> = emptyList(),
)

@Serializable
data class TmdbProductionCompany(
    val id: Int = 0,
    val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("origin_country") val originCountry: String = "",
)

@Serializable
data class TmdbCredits(val cast: List<TmdbCastMember> = emptyList())

@Serializable
data class TmdbCastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class TmdbPagedResults(val results: List<TmdbSearchResult> = emptyList())

@Serializable
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())

@Serializable
data class TmdbVideo(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val name: String = "",
)

// ---------------- Persona (actor/actriz) ----------------

@Serializable
data class TmdbPersonDetails(
    val id: Int = 0,
    val name: String = "",
    val biography: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    val birthday: String = "",
    @SerialName("place_of_birth") val placeOfBirth: String = "",
    @SerialName("combined_credits") val combinedCredits: TmdbCombinedCredits = TmdbCombinedCredits(),
)

@Serializable
data class TmdbCombinedCredits(val cast: List<TmdbCreditItem> = emptyList())

@Serializable
data class TmdbCreditItem(
    val id: Int = 0,
    @SerialName("media_type") val mediaType: String = "",
    val title: String = "",
    val name: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val character: String = "",
) {
    fun displayTitle(): String = title.ifBlank { name }
}

// ---------------- Productora ----------------

@Serializable
data class TmdbCompanyDetails(
    val id: Int = 0,
    val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    val description: String = "",
    val headquarters: String = "",
)

@Serializable
data class TmdbDiscoverResponse(val results: List<TmdbSearchResult> = emptyList())
