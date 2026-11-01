package eu.kanade.tachiyomi.extension.id.mirrorkomik

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class MirrorKomik : KeiSource() {
    private val xhrHeaders get() = headers.newBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    // Listings paginate with a last_id cursor and never say when they end, and the app treats an empty
    // page as an error, so the next page is fetched ahead and kept here until requested, keyed by listing url
    private val nextPages = ConcurrentHashMap<String, Pair<List<SManga>, LoadMoreDto>>()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".serieslist.pop li").map { li ->
            val link = li.selectFirst(".leftseries h2 a")!!
            SManga.create().apply {
                url = link.absUrl("href").toMangaPath()
                title = link.text()
                thumbnail_url = li.selectFirst("img")?.imgUrl()
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = loadMore(typeUrl("Komik"), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/loadmore-search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
        } else {
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug
            if (genre != null) {
                "$baseUrl/loadmore-genre".toHttpUrl().newBuilder()
                    .addQueryParameter("genre", genre)
                    .build()
            } else {
                typeUrl(filters.firstInstanceOrNull<TypeFilter>()?.value ?: "Komik")
            }
        }
        return loadMore(url, page)
    }

    private fun typeUrl(type: String) = "$baseUrl/loadmore-type".toHttpUrl().newBuilder()
        .addQueryParameter("type", type)
        .build()

    private suspend fun loadMore(url: HttpUrl, page: Int): MangasPage {
        val key = url.toString()
        val current = if (page == 1) fetchPage(url, null) else nextPages.remove(key) ?: return MangasPage(emptyList(), false)
        val (mangas, result) = current
        if (mangas.isEmpty() || result.lastId == null) return MangasPage(mangas, false)

        val next = fetchPage(url, result)
        val hasNextPage = next.first.isNotEmpty()
        if (hasNextPage) nextPages[key] = next else nextPages.remove(key)
        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun fetchPage(url: HttpUrl, previous: LoadMoreDto?): Pair<List<SManga>, LoadMoreDto> {
        val requestUrl = url.newBuilder().apply {
            previous?.lastId?.let { addQueryParameter("last_id", it.content) }
            previous?.lastScore?.let { addQueryParameter("last_score", it.content) }
        }.build()
        val result = client.get(requestUrl, xhrHeaders, Fetch.XHR).parseAs<LoadMoreDto>()
        val mangas = result.html?.let { Jsoup.parseBodyFragment(it, baseUrl) }
            ?.select("a.komik-card, .bsx > a")
            ?.map { it.toSManga() }
            .orEmpty()
        return mangas to result
    }

    private fun Element.toSManga() = SManga.create().apply {
        url = absUrl("href").toMangaPath()
        title = selectFirst("h3, .tt")!!.text()
        thumbnail_url = selectFirst("img")?.imgUrl()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull()?.lowercase() !in MANGA_TYPES || url.pathSegments.getOrNull(1).isNullOrEmpty()) return null
        val path = url.toString().toMangaPath()
        val document = client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup()
        return parseMangaDetails(document).apply { this.url = path }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            parseMangaDetails(document).apply { url = manga.url },
            parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")!!.text().removeSuffix(" Bahasa Indonesia")
        thumbnail_url = document.selectFirst(".thumb img")?.imgUrl()
        description = document.selectFirst(".entry-content[itemprop=description]")?.textOrNull()
        author = document.selectFirst(".fmed:has(b:contains(Author)) span")?.textOrNull()
        genre = buildList {
            document.select(".mgen a").mapTo(this) { it.text() }
            document.selectFirst(".tsinfo .imptdt:contains(Type) a")?.textOrNull()?.let(::add)
        }.joinToString()
        status = when (document.selectFirst(".tsinfo .imptdt:contains(Status) i")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // The chapter list only has view counts, so the latest chapter gets the series update date
    private fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select("#chapterlist li").map { li ->
            SChapter.create().apply {
                setUrlWithoutDomain(li.selectFirst("a")!!.absUrl("href"))
                name = li.selectFirst(".chapternum")!!.text()
            }
        }
        chapters.firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(
            document.selectFirst(".fmed:has(b:contains(Updated)) span")?.text(),
            ZONE,
        )
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        if (document.location().toHttpUrl().encodedPath == "/login") {
            throw Exception("Log in via WebView to read.")
        }

        val token = document.selectFirst("#thisch")!!.attr("data-token")
        val images = client.get("$baseUrl/chapter/listchap,$token", xhrHeaders, Fetch.XHR.from(document.location()))
            .parseAs<List<String>>()

        // Ads are mixed into the list at random positions, and "/media/" images go through the wp.com proxy, same as the site's reader
        return images
            .filterNot { "/public/img/" in it && "/public/img/Users/" !in it }
            .map { if ("/media/" in it) "https://i0.wp.com/" + it.substringAfter("://") else it }
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/genre/action", Fetch.DOCUMENT).asJsoup()
        .select(".nav_apb a")
        .map { GenreDto(it.text(), it.absUrl("href").toHttpUrl().pathSegments[1]) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>().orEmpty()
        return FilterList(
            listOfNotNull(
                Filter.Header("Filter diabaikan saat mencari dengan teks. Genre diutamakan daripada tipe."),
                TypeFilter(),
                genres.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
            ),
        )
    }

    private fun Element.imgUrl() = attrOrNull("data-src") ?: absUrl("src")

    private fun String.toMangaPath(): String {
        val segments = toHttpUrl().encodedPathSegments
        return "/${segments[0].lowercase()}/${segments[1]}"
    }

    companion object {
        private val MANGA_TYPES = setOf("manga", "manhwa", "manhua")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        private val ZONE = ZoneId.of("Asia/Jakarta")
    }
}
