package eu.kanade.tachiyomi.extension.en.mangaplaza

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class ContentList(
    @SerialName("html_content") val htmlContent: String,
    @SerialName("html_page") val htmlPage: String,
)

@Serializable
class GenreList(
    @SerialName("genre_info_list") val genres: List<Genre>,
    @SerialName("genre_tag_info_list") val tags: List<GenreTag>,
)

@Serializable
class Genre(
    @SerialName("genre_id") val id: String,
    @SerialName("genre_nm") val name: String,
)

@Serializable
class GenreTag(
    @SerialName("genre_tag_id") val id: String,
    @SerialName("genre_nm") val name: String,
)
