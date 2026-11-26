package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class XXXYaoi : KeiSource() {

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3, 1.seconds)
    }

    private val sitePages by lazy { SitePages({ baseUrl }) { headers["User-Agent"]!! } }

    override fun getHomeUrl() = "$baseUrl/biblioteca/"

    override suspend fun getPopularManga(page: Int) = libraryPage(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = sitePages.document(pageUrl(page), "loaded.js")
        val mangas = document.select(".page-item-detail").mapNotNull { card ->
            val link = card.selectFirst("h3 a[href]") ?: return@mapNotNull null
            val path = link.absUrl("href").toHttpUrl().encodedPath.takeIf(SERIES_PATH::matches) ?: return@mapNotNull null
            SManga.create().apply {
                url = path
                title = link.textOrNull() ?: return@mapNotNull null
                thumbnail_url = card.selectFirst("img")?.cover()
            }
        }
        return MangasPage(mangas, document.selectFirst("a[href=\"${pageUrl(page + 1)}\"]") != null)
    }

    private fun pageUrl(page: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        if (page > 1) addPathSegment("page").addPathSegment(page.toString()).addPathSegment("")
    }.build().toString()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = libraryPage(
        page = page,
        orderby = filters.firstInstance<SortFilter>().value,
        search = query.trim(),
        status = filters.firstInstance<StatusFilter>().value,
        genre = filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty(),
    )

    private suspend fun libraryPage(page: Int, orderby: String = DEFAULT_ORDER, search: String = "", status: String = "", genre: String = ""): MangasPage {
        val url = getHomeUrl().toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("pg", page.toString())
            if (orderby != DEFAULT_ORDER) addQueryParameter("orderby", orderby)
            if (search.isNotEmpty()) addQueryParameter("search", search)
            if (status.isNotEmpty()) addQueryParameter("status", status)
            if (genre.isNotEmpty()) addQueryParameter("genre", genre)
        }.build()
        val replaced = page > 1 || orderby != DEFAULT_ORDER || status.isNotEmpty() || genre.isNotEmpty()
        val document = sitePages.document(url.toString(), "library.js", replaced.toString())
        val mangas = document.select("a[href]:has(img[alt])").mapNotNull { link ->
            val path = link.absUrl("href").toHttpUrl().encodedPath.takeIf(SERIES_PATH::matches) ?: return@mapNotNull null
            val image = link.selectFirst("img[alt]")!!
            SManga.create().apply {
                this.url = path
                title = image.attrOrNull("alt")!!
                thumbnail_url = image.cover()
                this.status = link.selectFirst("[class*=status]")?.text().toStatus()
            }
        }.distinctBy { it.url }
        val hasNextPage = document.select("button").any { (it.ownText().toIntOrNull() ?: 0) > page }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = seriesDocument(getMangaUrl(manga))
        return SMangaUpdate(parseDetails(document).apply { url = manga.url }, parseChapters(document))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "bl" || url.pathSegments.size < 2) return null
        val seriesUrl = url.newBuilder().encodedPath("/").addPathSegment("bl").addPathSegment(url.pathSegments[1]).addPathSegment("").query(null).build()
        return parseDetails(seriesDocument(seriesUrl.toString())).apply { initialized = true }
    }

    private suspend fun seriesDocument(url: String) = sitePages.document(url, "loaded.js")

    private fun parseDetails(document: Document) = SManga.create().apply {
        url = document.location().toHttpUrl().encodedPath
        title = document.selectFirst("h1")!!.textOrNull()!!
        author = document.select("a[href*=/author/]").eachText().joinToString().ifBlank { null }
        artist = document.select("a[href*=/artist/]").eachText().joinToString().ifBlank { null }
        status = document.property("STATUS").toStatus()
        thumbnail_url = document.selectFirst(".xyaoi-cover-img")?.cover()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".xyaoi-synopsis-text"))
            .field("Lançamento", document.property("LANÇAMENTO"))
            .field("Classificação", document.property("CLASSIFICAÇÃO"))
            .field("Info", document.property("INFO"))
            .altTitles(document.selectFirst("#xyaoi-alt-content")?.text()?.split(", ").orEmpty(), "Nomes alternativos")
            .build()
        genre = buildList {
            document.property("TIPO")?.let(::add)
            addAll(document.select("a[rel=tag][href*='?genre=']").eachText())
        }.distinct().joinToString().ifBlank { null }
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val mangaPath = document.location().toHttpUrl().encodedPath
        val scan = document.property("SCAN")
        return document.select("li > a[href]").mapNotNull { link ->
            val path = link.absUrl("href").toHttpUrl().encodedPath
            if (!path.startsWith(mangaPath)) return@mapNotNull null
            val slug = path.removePrefix(mangaPath).trimEnd('/')
            if (slug.isEmpty() || '/' in slug) return@mapNotNull null
            SChapter.create().apply {
                url = slug
                name = link.selectFirst("header > *")!!.text()
                date_upload = parseDate(link.selectFirst("time")?.text())
                scanlator = scan
                memo = buildJsonObject { put("mangaPath", mangaPath) }
            }
        }.distinctBy { it.url }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaPath = chapter.memo.getStringOrNull("mangaPath") ?: throw IOException("Atualize a lista de capítulos")
        return baseUrl.toHttpUrl().newBuilder().encodedPath(mangaPath).addPathSegment(chapter.url).addPathSegment("").build().toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val read = sitePages.read(getChapterUrl(chapter), "reader.js", carrySession = true)
        val reader = read.document
        if (reader.pages.isEmpty()) {
            val document = reader.html.asJsoup(reader.url)
            if (document.selectFirst("iframe[src*=player]") != null) return emptyList()
            if (document.selectFirst("iframe[src*=challenges.cloudflare.com], input[name=cf-turnstile-response]") != null) {
                throw IOException(SitePages.SOLVE_IN_WEBVIEW)
            }
            throw IOException("As páginas do capítulo não carregaram, tente novamente")
        }
        return reader.pages.mapIndexed { index, imageUrl ->
            val siteHeaders = read.imageHeaders[imageUrl].orEmpty().toJsonString()
            Page(index, url = reader.url.toHttpUrl().newBuilder().fragment(siteHeaders).build().toString(), imageUrl = imageUrl)
        }
    }

    override fun getImageRequest(page: Page): Request {
        val siteHeaders = page.url.toHttpUrl().fragment?.parseAs<Map<String, String>>().orEmpty()
        return super.getImageRequest(page).newBuilder()
            .apply { siteHeaders.forEach { (name, value) -> header(name, value) } }
            .fetch(Fetch.XHR.from(page.url))
            .build()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = seriesDocument(getMangaUrl(manga))
        .select("a.xyaoi-scan-work-item")
        .mapNotNull { link ->
            val path = link.absUrl("href").toHttpUrl().encodedPath.takeIf(SERIES_PATH::matches) ?: return@mapNotNull null
            SManga.create().apply {
                url = path
                title = link.attrOrNull("title") ?: return@mapNotNull null
                thumbnail_url = link.selectFirst("img")?.cover()
            }
        }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>().orEmpty()
        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    override suspend fun fetchFilterData(): JsonElement = sitePages.document(getHomeUrl(), "library.js", "false")
        .select("[data-genre]")
        .mapNotNull { element ->
            val slug = element.attrOrNull("data-genre") ?: return@mapNotNull null
            Genre(element.selectFirst("> span")!!.text(), slug)
        }
        .distinctBy { it.slug }
        .toJsonElement()

    private fun Document.property(label: String) = selectFirst(":matchesOwn(^$label$) + *:not(:containsOwn(Não informado))")?.textOrNull()

    private fun Element.cover() = absUrl("src").ifEmpty { null }?.replace(RESIZED_REGEX, "")

    private fun String?.toStatus(): Int {
        val status = this?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "andamento" in status -> SManga.ONGOING
            "conclu" in status -> SManga.COMPLETED
            "hiatus" in status -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseDate(date: String?): Long {
        val value = date?.lowercase() ?: return 0
        val amount = AGO_REGEX.find(value)?.groupValues?.get(1)?.toLongOrNull() ?: return DATE_FORMAT.tryParseDate(value, ZoneOffset.UTC)
        val unit = when {
            "minuto" in value -> ChronoUnit.MINUTES
            "hora" in value -> ChronoUnit.HOURS
            "dia" in value -> ChronoUnit.DAYS
            else -> return 0
        }
        return ZonedDateTime.now(ZoneOffset.UTC).minus(amount, unit).toInstant().toEpochMilli()
    }

    companion object {
        private const val DEFAULT_ORDER = "latest"
        private val SERIES_PATH = Regex("""^/bl/[^/]+/$""")
        private val RESIZED_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
        private val AGO_REGEX = Regex("""^(\d+) .+ atrás$""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}
