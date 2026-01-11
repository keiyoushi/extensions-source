package eu.kanade.tachiyomi.extension.ja.kisslove

import eu.kanade.tachiyomi.extension.ja.kisslove.KissLove.Companion.DATE_FORMATTER
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
data class PagedManga(
    val currentPage: Long,
    val items: List<Manga>,
    val totalItems: Long,
    val totalPages: Long,
)

@Serializable
data class Manga(
    val artists: String?,
    val authors: String,
    val chapters: List<Chapter> = emptyList(),
    val cover: String,
    @SerialName("daily_views") val dailyViews: Long?,
    val description: String = "",
    val genres: String?,
    @SerialName("group_uploader") val groupUploader: Long?,
    val hidden: Int?,
    val id: Long,
    @SerialName("last_chapter") val lastChapter: Double,
    @SerialName("last_update") val lastUpdate: String?,
    @SerialName("m_status") val mStatus: Int,
    val name: String,
    @SerialName("other_name") val otherName: String,
    val released: Int?,
    val showads: Int?,
    val slug: String,
    val submitter: Long?,
    val total: Long?,
    @SerialName("trans_group") val transGroup: String,
    val views: Long,
    @SerialName("vote_count") val voteCount: Long?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@Manga.slug
        title = this@Manga.name
        artist = this@Manga.artists
        author = this@Manga.authors
        description = "${Jsoup.parse(this@Manga.description).text()}\n\n$otherName"
        thumbnail_url = this@Manga.cover
        genre = this@Manga.genres?.split(",")?.joinToString { it.trim() }
        status = if (mStatus == 1) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
data class Chapter(
    val chapter: Double,
    val content: String = "",
    val hidden: Int?,
    val id: Long,
    @SerialName("last_update") val lastUpdate: String,
    val manga: String?,
    val name: String?,
    val showads: Int?,
    val submitter: Long?,
    val views: Long?,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = "$id/$slug-chapter-$chapter"
        name = this@Chapter.name?.takeIf { it.isNotBlank() } ?: "Chapter $chapter"

        date_upload = runCatching {
            DATE_FORMATTER.tryParse(lastUpdate)
        }.getOrDefault(0L)
    }
}

@Serializable
data class Genre(
    val name: String,
)
