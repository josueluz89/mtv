package com.mtv.iptv.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("language") language: String = "es",
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "es",
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbSearchResponse

    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es",
        @Query("append_to_response") append: String = "credits,similar,videos,recommendations",
    ): TmdbMovieDetails

    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es",
        @Query("append_to_response") append: String = "credits,similar,videos,recommendations",
    ): TmdbTvDetails
}
