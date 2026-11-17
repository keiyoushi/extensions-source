package eu.kanade.tachiyomi.extension.all.ithinkilikeyou

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class IThinkILikeYou : KeiSource() {

    // The site's only list is its handful of series
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(getSeries().map { it.toSManga(baseUrl) }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val series = getSeries().filter { it.name.contains(query.trim(), ignoreCase = true) }
        return MangasPage(series.map { it.toSManga(baseUrl) }, false)
    }

    // Series links, and comic links from a series, which carry its id (?sid=): a comic's own page doesn't give its series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 2) return null
        val series = when (segments[0]) {
            "series" -> getSeries().firstOrNull { it.slug == segments[1] }
            "comic" -> url.queryParameter("sid")?.let { id -> getSeries().firstOrNull { it.id.toString() == id } }
            else -> null
        }
        return series?.toSManga(baseUrl)?.apply { initialized = true }
    }

    // The details come from the site's API, the comics from the series page, 30 at a time
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.trim('/').substringAfterLast('/')
        val details = async { if (fetchDetails) getSeries(slug).first().toSManga(baseUrl) else manga }
        val comics = async {
            if (!fetchChapters) return@async emptyList()
            val list = mutableListOf<SChapter>()
            var page: HttpUrl? = getMangaUrl(manga).toHttpUrl()
            while (page != null) {
                val document = client.get(page, Fetch.DOCUMENT).asJsoup()
                list += document.select("#comic-list li.comic").mapNotNull { it.toSChapter() }
                page = document.selectFirst("nav.pagination a.next")?.absUrl("href")?.toHttpUrl()
            }
            // Titles are free text: comics are numbered in order, so that numbers in titles aren't taken for chapter numbers
            list.onEachIndexed { index, chapter -> chapter.chapter_number = (list.size - index).toFloat() }
        }
        SMangaUpdate(details.await(), comics.await())
    }

    // The comic's page shows no images for a comic for patrons only (the API gives some of them), and adds a
    // "become a patron" button to the comic's own images
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#spliced-comic img[class*=wp-image-]")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    private suspend fun getSeries(slug: String? = null): List<SeriesDto> {
        val url = apiUrl("series").newBuilder()
            .apply { if (slug != null) addQueryParameter("slug", slug) }
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_embed", "wp:featuredmedia,wp:term")
            .addQueryParameter("_fields", "id,slug,title,content,_links.wp:featuredmedia,_links.wp:term,_embedded")
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<SeriesDto>>()
    }

    private fun apiUrl(type: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("wp-json/wp/v2")
        .addPathSegment(type)
        .build()

    private fun Element.toSChapter(): SChapter? {
        // A comic still to come is listed without a link
        val link = selectFirst("a[href]") ?: return null
        return SChapter.create().apply {
            url = link.absUrl("href").toHttpUrl().encodedPath
            val title = selectFirst(".comic-title")!!.text()
            name = if (selectFirst("img[src*=lockedTn]") != null) "🔒 $title" else title
            date_upload = DATE_FORMAT.tryParseDate(selectFirst(".comic-post-date")?.text())
        }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    }
}
