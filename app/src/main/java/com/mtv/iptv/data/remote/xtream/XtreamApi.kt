package com.mtv.iptv.data.remote.xtream

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Xtream Codes Player API.
 *
 * Las respuestas de listas son arreglos JSON y las de detalle son objetos;
 * por eso se devuelven como [JsonElement] y cada repositorio los decodifica.
 */
interface XtreamApi {

    @GET("player_api.php")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String,
    ): JsonElement

    @GET("player_api.php")
    suspend fun action(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("category_id") categoryId: String? = null,
        @Query("vod_id") vodId: Int? = null,
        @Query("series_id") seriesId: Int? = null,
    ): JsonElement
}
