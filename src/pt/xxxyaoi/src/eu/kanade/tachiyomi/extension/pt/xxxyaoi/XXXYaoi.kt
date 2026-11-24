package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class XXXYaoi : MadaraNoAjax() {

    override val supportsPostId = false

    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3, 1.seconds)
    }

    private val sitePages by lazy { SitePages({ baseUrl }) { headers["User-Agent"]!! } }

    override fun getHomeUrl() = "$baseUrl/biblioteca/"

    override suspend fun getPopularManga(page: Int) = libraryPage(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegment("page").addPathSegment(page.toString()).addPathSegment("")
        }.build()
        val document = sitePages.document(url.toString(), "loaded.js")
        val nextPage = baseUrl.toHttpUrl().newBuilder().addPathSegment("page").addPathSegment((page + 1).toString()).addPathSegment("").build().toString()
        return MangasPage(parseArchive(document), document.selectFirst("a[href=\"$nextPage\"]") != null)
    }

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
                thumbnail_url = processThumbnail(imageFromElement(image), true)
                this.status = link.selectFirst("[class*=status]")?.text()?.toStatus() ?: SManga.UNKNOWN
                memo = mangaMemo(path, emptyList())
            }
        }.distinctBy { it.url }
        val hasNextPage = document.select("button").any { (it.ownText().toIntOrNull() ?: 0) > page }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = seriesDocument(getMangaUrl(manga))
        return SMangaUpdate(parseDetails(document, "", manga.url), parseChapterList(document, document.location().toHttpUrl().encodedPath))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "bl" || url.pathSegments.size < 2) return null
        val seriesUrl = url.newBuilder().encodedPath("/").addPathSegment("bl").addPathSegment(url.pathSegments[1]).addPathSegment("").query(null).build()
        return parseDetails(seriesDocument(seriesUrl.toString()), "", preserveUrl = null).apply { initialized = true }
    }

    private suspend fun seriesDocument(url: String) = sitePages.document(url, "loaded.js")

    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorAuthor = "a[href*=/author/]"
    override val mangaDetailsSelectorArtist = "a[href*=/artist/]"
    override val mangaDetailsSelectorStatus = ":matchesOwn(^STATUS$) + *"
    override val mangaDetailsSelectorDescription = ".xyaoi-synopsis-text"
    override val mangaDetailsSelectorThumbnail = ".xyaoi-cover-img"
    override val mangaDetailsSelectorTag = "a[rel=tag][href*='?genre=']"
    override val seriesTypeSelector = ":matchesOwn(^TIPO$) + *:not(:containsOwn(Não informado))"

    override fun processThumbnail(url: String?, fromSearch: Boolean) = url?.replace(RESIZED_REGEX, "")

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document, id, preserveUrl).apply {
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(mangaDetailsSelectorDescription))
            .field("Lançamento", document.property("LANÇAMENTO"))
            .field("Classificação", document.property("CLASSIFICAÇÃO"))
            .field("Info", document.property("INFO"))
            .altTitles(document.selectFirst("#xyaoi-alt-content")?.text()?.split(", ").orEmpty(), "Nomes alternativos")
            .build()
        genre = buildList {
            document.property("TIPO")?.let(::add)
            addAll(document.select(mangaDetailsSelectorTag).eachText())
        }.distinct().joinToString().ifBlank { null }
    }

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        val scan = document.property("SCAN")
        return document.select("li > a[href]").mapNotNull { link ->
            val path = link.absUrl("href").toHttpUrl().encodedPath
            if (!path.startsWith(mangaPath)) return@mapNotNull null
            val slug = path.removePrefix(mangaPath).trimEnd('/')
            if (slug.isEmpty() || '/' in slug) return@mapNotNull null
            SChapter.create().apply {
                url = slug
                name = link.selectFirst("header > *")!!.text()
                date_upload = parseChapterDate(link.selectFirst("time")?.text())
                scanlator = scan
                memo = buildJsonObject { put("mangaPath", mangaPath) }
            }
        }.distinctBy { it.url }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = sitePages.document(getChapterUrl(chapter), "reader.js", carrySession = true)
        if (document.selectFirst("iframe[src*=player]") != null) return emptyList()
        val pages = document.select("img").mapNotNull { image ->
            image.attributes().firstOrNull { "/WP-manga/data/" in it.value }?.let { image.absUrl(it.key) }
        }
        if (pages.isEmpty() && document.selectFirst("iframe[src*=challenges.cloudflare.com], input[name=cf-turnstile-response]") != null) {
            throw IOException(SitePages.SOLVE_IN_WEBVIEW)
        }
        return pages.mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl) }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = seriesDocument(getMangaUrl(manga))
        .select("a.xyaoi-scan-work-item")
        .mapNotNull { link ->
            val path = link.absUrl("href").toHttpUrl().encodedPath.takeIf(SERIES_PATH::matches) ?: return@mapNotNull null
            SManga.create().apply {
                url = path
                title = link.attrOrNull("title") ?: return@mapNotNull null
                thumbnail_url = link.selectFirst("img")?.let { processThumbnail(imageFromElement(it), true) }
                memo = mangaMemo(path, emptyList())
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

    companion object {
        private const val DEFAULT_ORDER = "latest"
        private val SERIES_PATH = Regex("""^/bl/[^/]+/$""")
        private val RESIZED_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
