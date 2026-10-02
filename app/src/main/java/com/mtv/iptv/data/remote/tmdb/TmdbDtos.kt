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
)

@Serializable
data class TmdbCredits(val cast: List<TmdbCastMember> = emptyList())

@Serializable
data class TmdbCastMember(
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
