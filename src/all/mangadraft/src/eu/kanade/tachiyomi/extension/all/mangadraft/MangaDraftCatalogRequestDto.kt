package eu.kanade.tachiyomi.extension.all.mangadraft.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangadraftCatalogResponseDto(
    val data: List<MangadraftProjectDto> = emptyList(),
    val links: MangadraftLinksDto? = null,
    val meta: MangadraftMetaDto? = null,
)

/* ----------------------------- PROJECT ----------------------------- */

@Serializable
data class MangadraftProjectDto(
    val id: Int,
    val name: String,
    val slug: String,
    val avatar: String? = null,
    @SerialName("avatar_id") val avatarId: String? = null,
    @SerialName("avatar_animated") val avatarAnimated: Int? = null,
    val background: String? = null,

    @SerialName("nb_projects") val nbProjects: Int? = null,
    val likes: Int? = null,
    val views: Int? = null,
    val subscribers: Int? = null,
    val pages: Int? = null,

    @SerialName("project_type_label") val projectTypeLabel: String? = null,
    @SerialName("project_type") val projectType: MangadraftProjectTypeDto? = null,

    val user: MangadraftUserDto? = null,

    val genres: String? = null,
    @SerialName("genres_arr") val genresArr: List<String> = emptyList(),

    val description: String? = null,
    @SerialName("contest_id") val contestId: Int? = null,

    val url: String,
    @SerialName("catalog_url") val catalogUrl: String? = null,

    val language: Int? = null,

    // Can be false OR string like "il y a 5 heures"
    val new: kotlinx.serialization.json.JsonElement? = null,
)

/* ----------------------------- PROJECT TYPE ----------------------------- */

@Serializable
data class MangadraftProjectTypeDto(
    val name: String,
    val slug: String,
    val format: String,
)

/* ----------------------------- USER ----------------------------- */

@Serializable
data class MangadraftUserDto(
    val id: Int,
    val name: String,
    val slug: String,

    @SerialName("user_type_id") val userTypeId: Int? = null,

    val avatar: String? = null,
    @SerialName("avatar_sm") val avatarSm: String? = null,
    @SerialName("avatar_xs") val avatarXs: String? = null,
    @SerialName("avatar_id") val avatarId: String? = null,
    @SerialName("avatar_animated") val avatarAnimated: Int? = null,

    val cadre: String? = null,
    val banned: Boolean? = null,
    val url: String? = null,

    val subscribed: Boolean? = null,
    @SerialName("is_premium") val isPremium: Boolean? = null,
    @SerialName("nb_supporters") val nbSupporters: Int? = null,
    val hasShop: Int? = null,
)

/* ----------------------------- PAGINATION ----------------------------- */

@Serializable
data class MangadraftLinksDto(
    val first: String? = null,
    val last: String? = null,
    val prev: String? = null,
    val next: String? = null,
)

@Serializable
data class MangadraftMetaDto(
    @SerialName("current_page") val currentPage: Int,
    val from: Int? = null,
    val path: String,
    @SerialName("per_page") val perPage: String? = null,
    val to: Int? = null,
)
