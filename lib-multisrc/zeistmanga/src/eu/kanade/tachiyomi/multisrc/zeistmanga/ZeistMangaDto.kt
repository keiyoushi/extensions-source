package eu.kanade.tachiyomi.multisrc.zeistmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class ZeistMangaDto(
    val feed: ZeistMangaFeedDto? = null,
)

@Serializable
class ZeistMangaFeedDto(
    @SerialName("openSearch\$totalResults") val totalResults: TotalResult? = null,
    @SerialName("openSearch\$itemsPerPage") val itemsPerPage: TotalResult? = null,
    val category: List<ZeistMangaCategoryDto>? = emptyList(),
    val entry: List<ZeistMangaEntryDto>? = emptyList(),
)

@Serializable
class ZeistMangaEntryDto(
    val title: ZeistMangaEntryTitleDto? = null,
    val published: ZeistMangaEntryPublishedDto? = null,
    val updated: ZeistMangaEntryUpdatedDto? = null,
    val category: List<ZeistMangaEntryCategory>? = emptyList(),
    @SerialName("link") val url: List<ZeistMangaEntryLink>? = emptyList(),
    val content: ZeistMangaEntryContentDto? = null,
    @SerialName("media\$thumbnail") val thumbnail: ZeistMangaEntryThumbnail? = null,
) {
    fun toSManga(baseurl: String): SManga = SManga.create().apply {
        title = this@ZeistMangaEntryDto.title!!.t
        url = getChapterLink(this@ZeistMangaEntryDto.url!!).substringAfter(baseurl)
        thumbnail_url = if (this@ZeistMangaEntryDto.thumbnail == null) {
            getThumbnailFromContent(this@ZeistMangaEntryDto.content!!)
        } else {
            getThumbnail(this@ZeistMangaEntryDto.thumbnail)
        }
    }

    fun toSChapter(baseurl: String, dateUpload: Long = 0L): SChapter = SChapter.create().apply {
        name = this@ZeistMangaEntryDto.title!!.t
        url = getChapterLink(this@ZeistMangaEntryDto.url!!).substringAfter(baseurl)
        date_upload = dateUpload
    }

    fun getPublishedDate() = published?.t?.trim()

    fun getUpdatedDate() = updated?.t?.trim()

    private fun getChapterLink(list: List<ZeistMangaEntryLink>): String = list.first { it.rel == "alternate" }.href

    private fun getThumbnail(thumbnail: ZeistMangaEntryThumbnail): String = thumbnail.url.replace("""\/s.+?-c\/""".toRegex(), "/w600/")
        .replace("""=s(?!.*=s).+?-c$""".toRegex(), "=w600")

    private fun getThumbnailFromContent(html: ZeistMangaEntryContentDto): String {
        val document = Jsoup.parse(html.t)
        return document.selectFirst("img")!!.attr("src")
    }
}

@Serializable
class ZeistMangaCategoryDto(
    val term: String,
)

@Serializable
class ZeistMangaEntryTitleDto(
    @SerialName("\$t") val t: String,
)

@Serializable
class ZeistMangaEntryPublishedDto(
    @SerialName("\$t") val t: String,
)

@Serializable
class ZeistMangaEntryUpdatedDto(
    @SerialName("\$t") val t: String,
)

@Serializable
class ZeistMangaEntryContentDto(
    @SerialName("\$t") val t: String,
)

@Serializable
class TotalResult(
    @SerialName("\$t") val t: String,

)

@Serializable
class ZeistMangaEntryLink(
    val rel: String,
    val href: String,
)

@Serializable
class ZeistMangaEntryCategory(
    val term: String,
)

@Serializable
class ZeistMangaEntryThumbnail(
    val url: String,
)

@Serializable
class FeedUrl(
    val url: String,
    val old: Boolean,
    val new: Boolean,
    val category: String,
)
