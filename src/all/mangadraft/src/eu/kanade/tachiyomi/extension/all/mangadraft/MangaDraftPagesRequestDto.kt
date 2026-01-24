package eu.kanade.tachiyomi.extension.all.mangadraft.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangadraftPagesResponseDto(
    val data: List<MangadraftPageDto>,
)

@Serializable
data class MangadraftPageDto(
    val id: Int,

    @SerialName("project_category_id")
    val projectCategoryId: Int,

    @SerialName("page_number")
    val pageNumber: Int,

    val name: String?,

    val type: String,

    val text: String?,

    @SerialName("nb_likes")
    val nbLikes: Int,

    @SerialName("nb_views")
    val nbViews: Int,

    @SerialName("nb_comments")
    val nbComments: Int,

    @SerialName("published_at")
    val publishedAt: String,

    @SerialName("published_ago")
    val publishedAgo: String,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("updated_at")
    val updatedAt: String,

    val delayed: Boolean,

    @SerialName("published_date")
    val publishedDate: String,

    val nudity: Int,

    val image: MangadraftImageDto?,

    @SerialName("image_censored")
    val imageCensored: MangadraftImageDto?,

    val url: String,

    @SerialName("url_censored")
    val urlCensored: String?,

    val viewed: Boolean?,
)

@Serializable
data class MangadraftImageDto(
    val id: Int,
    val hash: String,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("updated_at")
    val updatedAt: String,

    val ago: String,

    val url: String,
    val img: String,

    val w: Int?,
    val h: Int?,
)
