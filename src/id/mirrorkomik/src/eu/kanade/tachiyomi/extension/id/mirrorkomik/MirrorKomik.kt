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
import keiyoushi.utils.getPreferencesLazy
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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class MirrorKomik : KeiSource() {
    // Each mirror shares the backend but serves its own frontend markup
    private enum class Frontend { INFO, CLICK, ASTRO }

    private val frontend
        get() = when (baseUrl.toHttpUrl().host) {
            "mirrorkomik.click" -> Frontend.CLICK
            "mirrorinkomik.my.id" -> Frontend.ASTRO
            else -> Frontend.INFO
        }

    private val preferences by getPreferencesLazy()

    private val xhrHeaders get() = headers.newBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    // Listings paginate with a last_id cursor and never say when they end, and the app treats an empty
    // page as an error, so the next page is fetched ahead and kept here until requested, keyed by listing url
    private val nextPages = ConcurrentHashMap<String, Pair<List<SManga>, LoadMoreDto>>()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = when (frontend) {
            Frontend.INFO -> document.select(".serieslist.pop li").map { li ->
                val link = li.selectFirst(".leftseries h2 a")!!
                SManga.create().apply {
                    url = link.absUrl("href").toMangaPath()
                    title = link.text()
                    thumbnail_url = li.selectFirst("img")?.imgUrl()
                }
            }
            Frontend.ASTRO -> document.select("[name=popular_updates] div[data-hk]:has(> a)").map { it.selectFirst("> a")!!.toSManga() }
            Frontend.CLICK -> document.select("[id^=Populer-] .at > a").map(::clickPopularToSManga)
        }
        return MangasPage(mangas, false)
    }

    // The popular block links to the latest chapter, so the series slug is taken back out of its url
    private fun clickPopularToSManga(link: Element) = SManga.create().apply {
        val heading = link.selectFirst("h3")!!.text()
        val number = heading.substringAfterLast(" Chapter ")
        val chapterSegment = link.absUrl("href").toHttpUrl().pathSegments.last()
        val slug = chapterSegment.removeSuffix("-$number").substringBeforeLast('-')
        val type = link.select("> div").firstNotNullOf { div -> div.classNames().firstOrNull { it.lowercase() in MANGA_TYPES } }
        url = "/${type.lowercase()}/$slug"
        title = heading.substringBeforeLast(" Chapter ")
        thumbnail_url = link.selectFirst("img")?.imgUrl()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = loadMore(typeUrl("Komik"), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/loadmore-search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
        } else {
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug
            when {
                genre == null -> typeUrl(filters.firstInstanceOrNull<TypeFilter>()?.value ?: "Komik")
                frontend == Frontend.ASTRO -> typeUrl(genre)
                else -> "$baseUrl/loadmore-genre".toHttpUrl().newBuilder()
                    .addQueryParameter("genre", genre)
                    .build()
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
            ?.select("a.komik-card, .bsx > a, .animposx > a, div.shrink-0 > a")
            ?.map { it.toSManga() }
            .orEmpty()
        return mangas to result
    }

    private fun Element.toSManga() = SManga.create().apply {
        url = absUrl("href").toMangaPath()
        val image = selectFirst("img")
        title = selectFirst("h3, h4, .tt")?.text() ?: image!!.attr("title")
        thumbnail_url = image?.imgUrl()
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
            parseChapterList(document, manga.url),
        )
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        when (frontend) {
            Frontend.INFO -> {
                title = document.selectFirst("h1.entry-title")!!.text().removeSuffix(" Bahasa Indonesia")
                thumbnail_url = document.selectFirst(".thumb img")?.imgUrl()
                description = document.selectFirst(".entry-content[itemprop=description]")?.textOrNull()
                author = document.selectFirst(".fmed:has(b:contains(Author)) span")?.textOrNull()
                genre = genreOf(document.select(".mgen a"), document.selectFirst(".tsinfo .imptdt:contains(Type) a")?.text())
                status = parseStatus(document.selectFirst(".tsinfo .imptdt:contains(Status) i")?.text())
            }
            Frontend.CLICK -> {
                title = document.selectFirst("header.jds h1")!!.text()
                thumbnail_url = document.selectFirst(".ims img")?.imgUrl()
                description = document.select("#Sinopsis p").joinToString("\n\n") { it.text() }.ifEmpty { null }
                author = document.selectFirst(".inftable tr:has(td:contains(Komikus)) a")?.textOrNull()
                genre = genreOf(document.select("ul.genre a"), document.selectFirst(".inftable tr:has(td:contains(Jenis)) td:last-child")?.text())
                status = parseStatus(document.selectFirst(".inftable tr:has(td:contains(Status)) td:last-child")?.text())
            }
            Frontend.ASTRO -> {
                title = document.selectFirst("main h3 a.link")!!.text()
                thumbnail_url = document.selectFirst("main img.not-prose")?.imgUrl()
                description = document.selectFirst(".limit-html")?.select(".limit-html-p")?.joinToString("\n\n") { it.text() }
                author = document.selectFirst("main a[href*=cari?s=]")?.textOrNull()
                genre = genreOf(
                    document.select("main div:has(> b:contains(Genres)) > span > span:first-child"),
                    document.selectFirst("main h3 button img")?.attr("alt"),
                )
                status = parseStatus(document.selectFirst("main h3 + div > span")?.text())
            }
        }
    }

    private fun genreOf(genres: List<Element>, type: String?) = (genres.map { it.text() } + listOfNotNull(type?.takeIf { it.isNotBlank() }))
        .joinToString()

    private fun parseStatus(status: String?) = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseChapterList(document: Document, mangaUrl: String): List<SChapter> {
        val (type, slug) = mangaUrl.split('/').filter { it.isNotEmpty() }
        val chapters = when (frontend) {
            Frontend.INFO -> document.select("#chapterlist li").map { li ->
                chapterOf(li.selectFirst("a")!!, li.selectFirst(".chapternum")!!.text(), type, slug)
            }
            Frontend.CLICK -> document.select("li.chapter-item a.chapter-link").map { link ->
                chapterOf(link, link.selectFirst(".chapter-number")!!.text(), type, slug)
            }
            Frontend.ASTRO -> document.select("div.flex-col-reverse div[data-hk]:has(a.link-primary)").map { row ->
                val link = row.selectFirst("a.link-primary")!!
                chapterOf(link, link.text(), type, slug).apply {
                    date_upload = parseRelativeDate(row.selectFirst("time")?.text())
                }
            }
        }
        // The chapter list only has view counts, so the latest chapter gets the series update date
        if (frontend == Frontend.INFO) {
            chapters.firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(
                document.selectFirst(".fmed:has(b:contains(Updated)) span")?.text(),
                ZONE,
            )
        }
        return chapters
    }

    // Chapter urls carry a token that differs per mirror (/chapter/{slug}-{token}-{number}), so it is stored without the token as /{type}/{slug}/{number} and the active mirror's token is added back.
    // The name is built from the number too, since mirrors label chapters differently ("Ch. 1" or "Chapter 1") and a renamed chapter loses its downloads after switching mirrors
    private fun chapterOf(link: Element, chapterName: String, type: String, slug: String) = SChapter.create().apply {
        val chapterUrl = link.absUrl("href").toHttpUrl()
        val segment = chapterUrl.encodedPathSegments.last()
        if (segment.startsWith("$slug-")) {
            val rest = segment.removePrefix("$slug-")
            saveToken(rest.substringBefore('-'))
            url = "/$type/$slug/${rest.substringAfter('-')}"
            name = "Chapter " + chapterUrl.pathSegments.last().removePrefix("$slug-").substringAfter('-')
        } else {
            url = chapterUrl.encodedPath
            name = chapterName
        }
    }

    private val tokenKey get() = "token_${baseUrl.toHttpUrl().host}"

    private fun saveToken(token: String) {
        if (preferences.getString(tokenKey, null) != token) {
            preferences.edit().putString(tokenKey, token).apply()
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith("/chapter/")) return baseUrl + chapter.url
        val (type, slug, number) = chapter.url.split('/', limit = 4).drop(1)
        val token = preferences.getString(tokenKey, null) ?: return "$baseUrl/$type/$slug"
        return "$baseUrl/chapter/$slug-$token-$number"
    }

    private suspend fun refreshToken(chapter: SChapter) {
        val mangaUrl = chapter.url.substringBeforeLast('/')
        parseChapterList(client.get(baseUrl + mangaUrl, Fetch.DOCUMENT).asJsoup(), mangaUrl)
    }

    private suspend fun fetchChapterPage(chapter: SChapter): Document {
        val tokenless = !chapter.url.startsWith("/chapter/")
        if (tokenless && preferences.getString(tokenKey, null) == null) refreshToken(chapter)

        var document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // A stale token redirects to the home page
        if (tokenless && document.location().toHttpUrl().encodedPath == "/") {
            refreshToken(chapter)
            document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        }
        if (document.location().toHttpUrl().encodedPath == "/login") {
            throw Exception("Log in via WebView to read.")
        }
        return document
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = fetchChapterPage(chapter)
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
        .select(".nav_apb a, ul.genre a")
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

    private fun parseRelativeDate(date: String?): Long {
        val text = date?.lowercase() ?: return 0L
        val now = ZonedDateTime.now(ZONE)
        if (text == "kemarin") return now.minusDays(1).toInstant().toEpochMilli()
        val match = RELATIVE_DATE_REGEX.find(text) ?: return 0L
        val unit = when (match.groupValues[2]) {
            "detik" -> ChronoUnit.SECONDS
            "menit" -> ChronoUnit.MINUTES
            "jam" -> ChronoUnit.HOURS
            "hari" -> ChronoUnit.DAYS
            "minggu" -> ChronoUnit.WEEKS
            "bulan" -> ChronoUnit.MONTHS
            else -> ChronoUnit.YEARS
        }
        return now.minus(match.groupValues[1].toLong(), unit).toInstant().toEpochMilli()
    }

    companion object {
        private val MANGA_TYPES = setOf("manga", "manhwa", "manhua")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        private val ZONE = ZoneId.of("Asia/Jakarta")
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(detik|menit|jam|hari|minggu|bulan|tahun)""")
    }
}
