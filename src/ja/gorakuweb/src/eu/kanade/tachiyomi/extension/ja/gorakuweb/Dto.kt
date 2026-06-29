package eu.kanade.tachiyomi.extension.ja.gorakuweb

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
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
        val seriesUrl = baseUrl.toHttpUrl().resolve(href)!!
        url = seriesUrl.pathSegments[1]
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
        description = seriesDescription?.let { Jsoup.parse(it).text() }
        author = this@EpisodeProps.author
        thumbnail_url = seriesThumbnailUrl
    }
}

@Serializable
class EpisodeEntry(
    private val href: String,
    private val title: String,
    private val openAt: String?,
    val disabled: Boolean?,
) {
    fun toSChapter() = SChapter.create().apply {
        val lock = if (disabled == true) "🔒 " else ""
        url = href
        name = lock + title
        date_upload = dateFormat.tryParse(openAt)
    }
}

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)

@Serializable
class PageMetadata(
    val pages: List<PageEntry>,
)

@Serializable
class PageEntry(
    val filename: String,
    val page: Int,
)
