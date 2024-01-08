package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MpcResponse(
    @SerialName("mpcEpisodesDto") val episodes: MpcEpisodesDto? = null,
    @SerialName("mpcTitlesDto") val titles: MpcTitlesDto? = null,
    val pageList: List<MpcPage>? = emptyList(),
)

@Serializable
data class MpcEpisodesDto(
    val pagination: MpcPagination? = null,
    val episodeList: List<MpcEpisode>? = emptyList(),
)

@Serializable
data class MpcTitlesDto(
    val pagination: MpcPagination? = null,
    val titleList: List<MpcTitle>? = emptyList(),
)

@Serializable
data class MpcPagination(
    val page: Int,
    val maxPage: Int,
) {

    val hasNextPage: Boolean
        get() = page < maxPage
}

@Serializable
data class MpcTitle(
    @SerialName("titleId") val id: String,
    val title: String,
    val thumbnailUrl: String,
) {

    fun toSManga(): SManga = SManga.create().apply {
        title = this@MpcTitle.title
        thumbnail_url = thumbnailUrl
        url = "/titles/$id"
    }
}

@Serializable
data class MpcEpisode(
    @SerialName("episodeId") val id: String,
    @SerialName("episodeTitle") val title: String,
    val numbering: Int,
    val oneshot: Boolean = false,
    val publishDate: Long,
) {

    fun toSChapter(): SChapter = SChapter.create().apply {
        name = if (oneshot) "One-shot" else title
        date_upload = publishDate
        url = "/episodes/$id"
    }
}

@Serializable
data class MpcPage(val publicBgImage: String)
