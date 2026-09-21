package eu.kanade.tachiyomi.extension.ja.gorakuweb

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SeriesList(
    val cardList: List<List<Entries>>,
)

@Serializable
class Entries(
    private val href: String,
    private val imageSrc: String?,
    private val title: String,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = baseUrl.toHttpUrl().resolve(href)!!.pathSegments[1]
        title = this@Entries.title
        thumbnail_url = imageSrc
    }
}

@Serializable
class EpisodeProps(
    private val seriesTitle: String,
    private val seriesDescription: String?,
    private val author: String?,
    private val seriesThumbnailUrl: String?,
    private val shareUrl: String,
    val episodeList: List<EpisodeEntry>,
    val base: String,
    val metadata: PageMetadata,
    val accessKey: String,
    val keyBytes: String,
    val ivBytes: String,
) {
    fun toSManga() = SManga.create().apply {
        url = shareUrl.toHttpUrl().pathSegments[1]
        title = seriesTitle
        description = seriesDescription?.let { Jsoup.parseBodyFragment(it).wholeText() }
        author = this@EpisodeProps.author
        thumbnail_url = seriesThumbnailUrl
    }
}

@Serializable
class EpisodeEntry(
    private val href: String,
    private val title: String,
    private val openAt: String?,
    private val disabled: Boolean?,
) {
    val isLocked: Boolean
        get() = disabled == true

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = href
        name = lock + title
        date_upload = dateFormat.tryParseDate(openAt)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))

@Serializable
class PageMetadata(
    val pages: List<PageEntry>,
)

@Serializable
class PageEntry(
    val filename: String,
    val page: Int,
)
