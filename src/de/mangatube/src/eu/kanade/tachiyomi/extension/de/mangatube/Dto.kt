package eu.kanade.tachiyomi.extension.de.mangatube

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

@Serializable
class Challenge(
    val tk: String,
    val arg1: String,
    val arg2: String,
    val arg3: String,
)

@Serializable
class QuickSearchResponse(private val data: List<QuickSearchResponseData> = emptyList()) {
    val mangas get() = data.map { it.toSManga() }
}

@Serializable
class QuickSearchResponseData(
    private val title: String,
    private val url: String,
    private val cover: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@QuickSearchResponseData.title
        url = this@QuickSearchResponseData.url
        thumbnail_url = cover
    }
}

@Serializable
class LatestUpdatesResponse(private val data: LatestUpdatesData) {
    val mangas get() = data.published.map { it.manga.toSManga() }.distinctBy { it.url }
}

@Serializable
class LatestUpdatesData(val published: List<LatestUpdatesEntry> = emptyList())

@Serializable
class LatestUpdatesEntry(val manga: LatestUpdatesManga)

@Serializable
class LatestUpdatesManga(
    private val title: String,
    private val cover: String,
    private val url: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@LatestUpdatesManga.title
        url = this@LatestUpdatesManga.url
        thumbnail_url = cover
    }
}

@Serializable
class TopMangaResponse(private val data: TopMangaData) {
    val mangas get() = data.manga.map { it.toSManga() }
}

@Serializable
class TopMangaData(val manga: List<TopMangaEntry> = emptyList())

@Serializable
class TopMangaEntry(
    private val title: String,
    private val cover: String,
    private val url: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@TopMangaEntry.title
        url = this@TopMangaEntry.url
        thumbnail_url = cover
    }
}

@Serializable
class MangaDetailsResponse(private val data: MangaDetailsData) {
    fun toSManga() = data.manga.toSManga()
}

@Serializable
class MangaDetailsData(val manga: MangaDetailsManga)

@Serializable
class MangaDetailsManga(
    private val title: String,
    private val description: String = "",
    private val cover: String,
    private val url: String,
    private val status: Int,
    private val author: List<MangaDetailsPerson> = emptyList(),
    private val artist: List<MangaDetailsPerson> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDetailsManga.title
        url = this@MangaDetailsManga.url
        thumbnail_url = cover
        description = this@MangaDetailsManga.description
        author = this@MangaDetailsManga.author.joinToString { it.name }
        artist = this@MangaDetailsManga.artist.joinToString { it.name }
        status = when (this@MangaDetailsManga.status) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class MangaDetailsPerson(val name: String)

@Serializable
class MangaChaptersResponse(private val data: MangaChaptersData) {
    fun toSChapters(slug: String): List<SChapter> = data.chapters.map { it.toSChapter(slug) }
}

@Serializable
class MangaChaptersData(val chapters: List<MangaChapter> = emptyList())

@Serializable
class MangaChapter(
    private val id: Long,
    private val number: Double,
    private val subNumber: Double,
    private val volume: Double,
    private val name: String = "",
    private val publishedAt: String = "",
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        name = buildString {
            if (volume > 0) {
                append("Vol. ")
                append(volume.toString().removeSuffix(".0"))
                append(" ")
            }
            append("Ch. ")
            append(number.toString().removeSuffix(".0"))
            if (subNumber > 0) {
                append(".")
                append(subNumber.toString().removeSuffix(".0"))
            }
            if (this@MangaChapter.name.isNotEmpty()) {
                append(" - ")
                append(this@MangaChapter.name)
            }
        }
        url = "/api/manga/$slug/chapter/$id"
        date_upload = dateFormat.tryParse(publishedAt)
    }
}

@Serializable
class ChapterDetailsResponse(private val data: ChapterDetailsData) {
    val pages get() = data.chapter.pages
}

@Serializable
class ChapterDetailsData(val chapter: ChapterDetails)

@Serializable
class ChapterDetails(val pages: List<ChapterPage> = emptyList())

@Serializable
class ChapterPage(
    private val url: String = "",
    @SerialName("alt_source") private val altSource: String = "",
    val page: Int,
) {
    val imageUrl get() = url.ifEmpty { altSource }
}
