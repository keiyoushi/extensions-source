package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MpcResponse(
    val status: String,
    @SerialName("mpcTitlesDto") val titles: MpcTitlesDto? = null,
)

@Serializable
data class MpcTitlesDto(
    val titleList: List<MpcTitle>? = emptyList(),
)

@Serializable
data class MpcTitle(
    @SerialName("titleId") val id: String,
    val title: String,
    val thumbnail: String,
    @SerialName("is_one_shot") val isOneShot: Boolean,
    val author: MpcAuthorDto,
    @SerialName("latest_episode") val latestEpisode: MpcLatestEpisode,
    ) {

    fun toSManga(): SManga = SManga.create().apply {
        title = this@MpcTitle.title
        thumbnail_url = thumbnail
        url = "/titles/${latestEpisode.titleConnectId}"
        author = this@MpcTitle.author.name
    }
}

@Serializable
data class MpcAuthorDto(
    val name: String,
)

@Serializable
data class MpcLatestEpisode(
    @SerialName("title_connect_id") val titleConnectId: String,
)

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
