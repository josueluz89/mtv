package com.mtv.iptv.data.remote.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamCategory(
    @SerialName("category_id")
    @Serializable(with = LenientStringSerializer::class)
    val categoryId: String = "",
    @SerialName("category_name")
    val categoryName: String = "",
    @SerialName("parent_id")
    @Serializable(with = LenientIntSerializer::class)
    val parentId: Int = 0,
)

@Serializable
data class XtreamLiveStream(
    @SerialName("stream_id")
    @Serializable(with = LenientIntSerializer::class)
    val streamId: Int = 0,
    val name: String = "",
    @SerialName("stream_icon")
    val streamIcon: String = "",
    @SerialName("category_id")
    @Serializable(with = LenientStringSerializer::class)
    val categoryId: String = "",
    @SerialName("epg_channel_id")
    val epgChannelId: String = "",
    val added: String = "",
)

@Serializable
data class XtreamVodStream(
    @SerialName("stream_id")
    @Serializable(with = LenientIntSerializer::class)
    val streamId: Int = 0,
    val name: String = "",
    @SerialName("stream_icon")
    val streamIcon: String = "",
    @SerialName("container_extension")
    val containerExtension: String = "mp4",
    @SerialName("category_id")
    @Serializable(with = LenientStringSerializer::class)
    val categoryId: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val rating: String = "",
    val added: String = "",
)

@Serializable
data class XtreamSeries(
    @SerialName("series_id")
    @Serializable(with = LenientIntSerializer::class)
    val seriesId: Int = 0,
    val name: String = "",
    val cover: String = "",
    val plot: String = "",
    val cast: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val rating: String = "",
    @SerialName("category_id")
    @Serializable(with = LenientStringSerializer::class)
    val categoryId: String = "",
    val added: String = "",
)

@Serializable
data class VodInfoResponse(
    val info: VodInfoDetails = VodInfoDetails(),
    @SerialName("movie_data")
    val movieData: VodMovieData = VodMovieData(),
)

@Serializable
data class VodInfoDetails(
    @SerialName("movie_image")
    val movieImage: String = "",
    val plot: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val rating: String = "",
    val releasedate: String = "",
    val duration: String = "",
    val director: String = "",
    val cast: String = "",
    val genre: String = "",
)

@Serializable
data class VodMovieData(
    @SerialName("stream_id")
    @Serializable(with = LenientIntSerializer::class)
    val streamId: Int = 0,
    val name: String = "",
    @SerialName("container_extension")
    val containerExtension: String = "mp4",
)

@Serializable
data class SeriesInfoResponse(
    val info: SeriesInfoDetails = SeriesInfoDetails(),
    /** Clave = número de temporada ("1", "2", ...). */
    val episodes: Map<String, List<XtreamEpisode>> = emptyMap(),
)

@Serializable
data class SeriesInfoDetails(
    val name: String = "",
    val cover: String = "",
    val plot: String = "",
    val cast: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val rating: String = "",
    @SerialName("releaseDate")
    val releaseDate: String = "",
    val genre: String = "",
)

@Serializable
data class XtreamEpisode(
    @Serializable(with = LenientStringSerializer::class)
    val id: String = "",
    @SerialName("episode_num")
    @Serializable(with = LenientIntSerializer::class)
    val episodeNum: Int = 0,
    val title: String = "",
    @SerialName("container_extension")
    val containerExtension: String = "mp4",
    val info: EpisodeInfo = EpisodeInfo(),
)

@Serializable
data class EpisodeInfo(
    @SerialName("movie_image")
    val movieImage: String = "",
    val plot: String = "",
    @Serializable(with = LenientStringSerializer::class)
    val rating: String = "",
    val duration: String = "",
)
