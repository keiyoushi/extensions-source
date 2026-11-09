package eu.kanade.tachiyomi.extension.en.pornhwame

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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class PornhwaMe : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listingUrl("trending", page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listingUrl("latest-update", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return getMangaList(listingUrl(filters.firstInstanceOrNull<ListingFilter>()?.path ?: "trending", page))
        }
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment(query.trim())
            .addPathSegment("")
            .build()
        return getMangaList(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "read") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/read/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("article h1")!!.text()
            thumbnail_url = document.selectFirst("[itemprop=image] img")?.absUrl("src")
            author = document.info("Author")
            artist = document.info("Artist")
            status = when (document.info("Status")) {
                "on-going" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = (document.select("article a[href^=/genre/]").eachText() + listOfNotNull(document.info("Type")))
                .distinct()
                .joinToString()
                .ifEmpty { null }
            val synopsis = document.select("script[type=application/ld+json]")
                .map { it.data().parseAs<SeriesLdDto>() }
                .firstOrNull { it.type == "CreativeWorkSeries" }
                ?.description
            val altTitle = document.info("Alternative Title")?.removeSurrounding("(", ")")
                ?.takeUnless { it.contains(title, ignoreCase = true) }
            description = MarkdownDescription.Builder()
                .description(synopsis)
                .altTitles(listOfNotNull(altTitle))
                .build()
        }
        val dates = document.chapterDates()
        val chapterList = document.select("ul#chapter-list > li[data-ch]").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.selectFirst("span")!!.text()
                date_upload = dates[element.attr("data-ch")] ?: 0L
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("img.chapter-img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private fun listingUrl(path: String, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(path)
        .apply { if (page > 1) addPathSegment(page.toString()) }
        .addPathSegment("")
        .build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("#komikbaru [itemtype$=ComicSeries]").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("a[itemprop=url][title]")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst("[itemprop=name]")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.let(::originalImage)
            }
        }
        // Listings keep a "next" link past their last page
        return MangasPage(mangas, mangas.isNotEmpty() && document.selectFirst("link[rel=next]") != null)
    }

    private fun Document.info(label: String): String? = selectFirst("span:has(> b:containsOwn($label))")
        ?.let { it.text().removePrefix(it.selectFirst("b")!!.text()).trim() }
        ?.takeUnless { it.isEmpty() || it == "-" }

    // Chapter dates are only in the page's Qwik state, whose objects hold the base-36 indexes of their values
    private fun Document.chapterDates(): Map<String, Long> {
        val objs = selectFirst("script[type=qwik/json]")?.data()?.parseAs<QwikStateDto>()?.objs ?: return emptyMap()
        fun JsonObject.value(key: String) = objs[getString(key).toInt(36)].string
        return objs.filterIsInstance<JsonObject>()
            .filter { it.keys == CHAPTER_KEYS }
            .associate { it.value("chapter") to Instant.tryParse(it.value("created_at").dropWhile(Char::isISOControl)) }
    }

    // Cards show covers resized by the i0.wp.com image service, which keeps the original URL in its path
    private fun originalImage(url: String): String {
        val httpUrl = url.toHttpUrl()
        return if (httpUrl.host.endsWith(".wp.com")) "https://${httpUrl.encodedPath.removePrefix("/")}" else url
    }

    companion object {
        private val CHAPTER_KEYS = setOf("id", "chapter", "created_at")
    }
}
