package eu.kanade.tachiyomi.extension.all.nhentaiwebsite

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

@Source
abstract class NHentaiWebsite : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "zh" -> LANGUAGE_CHINESE
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    // The mirrors share no "popular" or "latest" listing path, but all of them list a language at /language/<lang>/
    override val supportsLatest = false

    override fun popularMangaSelector() = "div.gallery"

    override fun popularMangaNextPageSelector() = "a.next, .pagination .current + a.page"

    // nhentai.website serves galleries at /read/<id> and redirects /g/<id> there; the other mirrors only know /g/<id>
    override fun Element.mangaUrl() = selectFirst("a.cover")?.absUrl("href")?.replace("/read/", "/g/")

    override fun Element.mangaThumbnail() = selectFirst("img")?.imgAttr()

    override val idPrefixUri = "g"

    override fun basicSearchUrl(page: Int, query: String, filters: FilterList) = "$baseUrl/search".toHttpUrl().newBuilder()
        .addQueryParameter("q", query)
        .build()
        .toString()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in listOf("g", "read")) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        return getMangaById(id)
    }

    override val mangaDetailInfoSelector = "#bigcontainer"

    override fun Element.getCover() = selectFirst("#cover img")?.imgAttr()

    override fun getInfoSelector(tag: String) = "#info .field-name:contains($tag) .tags a"

    override fun Element.infoTagName() = selectFirst(".name")?.ownText() ?: ""

    override fun Element.totalPages() = selectFirst("#info > div:matchesOwn(^\\d+ pages$)")?.ownText()?.substringBefore(' ').orEmpty()

    override fun Element.getTime(): Long {
        val date = selectFirst("#info time[datetime]")?.attr("datetime")
        return Instant.tryParse(date).takeIf { it != 0L } ?: DATE_FORMAT.tryParseDateTime(date, ZoneOffset.UTC)
    }

    override fun parseChapterList(document: Document, url: String): List<SChapter> = super.parseChapterList(document, url.replace("/read/", "/g/"))

    // Not every mirror lists all thumbnails, but all of them serve the pages next to the cover
    override suspend fun pageListParse(document: Document): List<Page> {
        val imagesUrl = document.selectFirst("#cover img")!!.imgAttr().substringBeforeLast('/')
        return (1..document.totalPages().toInt()).map { Page(it - 1, imageUrl = "$imagesUrl/$it.webp") }
    }

    override val supportsFilterFetching = false

    override fun getFilterList(data: JsonElement?) = FilterList()

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
