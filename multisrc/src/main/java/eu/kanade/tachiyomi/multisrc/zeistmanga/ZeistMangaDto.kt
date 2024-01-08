package eu.kanade.tachiyomi.multisrc.zeistmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

private val DATE_FORMATTER by lazy {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
}

private fun parseDate(dateStr: String): Long {
    return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
        .getOrNull() ?: 0L
}

@Serializable
data class ZeistMangaDto(
    val feed: ZeistMangaFeedDto? = null,
)

@Serializable
data class ZeistMangaFeedDto(
    val entry: List<ZeistMangaEntryDto>? = emptyList(),
)

@Serializable
data class ZeistMangaEntryDto(
    val title: ZeistMangaEntryTitleDto? = null,
    val published: ZeistMangaEntryPublishedDto? = null,
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

    fun toSChapter(baseurl: String): SChapter = SChapter.create().apply {
        name = this@ZeistMangaEntryDto.title!!.t
        url = getChapterLink(this@ZeistMangaEntryDto.url!!).substringAfter(baseurl)
        val chapterDate = this@ZeistMangaEntryDto.published!!.t.trim()
        date_upload = parseDate(chapterDate)
    }

    private fun getChapterLink(list: List<ZeistMangaEntryLink>): String {
        return list.first { it.rel == "alternate" }.href
    }

    private fun getThumbnail(thumbnail: ZeistMangaEntryThumbnail): String {
        return thumbnail.url.replace("""\/s.+?-c\/""".toRegex(), "/w600/")
            .replace("""=s(?!.*=s).+?-c$""".toRegex(), "=w600")
    }

    private fun getThumbnailFromContent(html: ZeistMangaEntryContentDto): String {
        val document = Jsoup.parse(html.t)
        return document.selectFirst("img")!!.attr("src")
    }
}

@Serializable
data class ZeistMangaEntryTitleDto(
    @SerialName("\$t") val t: String,
)

@Serializable
data class ZeistMangaEntryPublishedDto(
    @SerialName("\$t") val t: String,
)

@Serializable
data class ZeistMangaEntryContentDto(
    @SerialName("\$t") val t: String,
)

@Serializable
data class ZeistMangaEntryLink(
    val rel: String,
    val href: String,
)

@Serializable
data class ZeistMangaEntryCategory(
    val term: String,
)

@Serializable
data class ZeistMangaEntryThumbnail(
    val url: String,
)
