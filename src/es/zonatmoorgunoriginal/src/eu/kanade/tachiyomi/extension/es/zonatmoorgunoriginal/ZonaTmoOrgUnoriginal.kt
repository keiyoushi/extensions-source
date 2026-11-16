package eu.kanade.tachiyomi.extension.es.zonatmoorgunoriginal

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ZonaTmoOrgUnoriginal : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
    }

    private val ajaxHeaders: Headers
        get() =
            headersBuilder()
                .set("Referer", "$baseUrl/biblioteca")
                .set("X-Requested-With", "XMLHttpRequest")
                .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList(null))

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url =
            "$baseUrl/ultimas-subidas"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()

        val seenMangaUrls = mutableSetOf<String>()
        val mangas =
            document.select(".upload-file-row").mapNotNull { element ->
                val title = element.selectFirst(".upc__title")?.textOrNull() ?: return@mapNotNull null
                val type = element.attrOrNull("data-zt-type")
                val thumbnailUrl = element.selectFirst("img.upc__img")?.absUrl("src")
                val mangaUrl = mangaIdentityUrl(title, type)
                if (!seenMangaUrls.add(mangaUrl)) return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(mangaUrl)
                    this.title = title
                    thumbnail_url = thumbnailUrl
                }
            }

        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun mangaIdentityUrl(
        title: String,
        type: String?,
    ): String = "$baseUrl/biblioteca"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("title", title.lowercase(Locale.ROOT))
        .apply { type?.let { addQueryParameter("type", it) } }
        .build()
        .toString()

    private suspend fun resolveLatestMangaUrl(
        title: String,
        type: String?,
    ): String {
        val searchUrl =
            "$baseUrl/api/search/suggest"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", title)
                .build()
        return client
            .get(searchUrl, Fetch.XHR)
            .parseAs<List<SearchSuggestionDto>>()
            .firstNotNullOfOrNull { it.mangaUrl(title, type) }
            ?: throw Exception("No se encontró la ficha del manga")
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/biblioteca".toHttpUrl().newBuilder().apply {
            addQueryParameter("title", query.trim())
            addQueryParameter("_pg", "1")
            filters.firstInstanceOrNull<SearchByFilter>()?.let { addQueryParameter("filter_by", it.selected) }
            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("order_item", it.sort)
                addQueryParameter("order_dir", it.dir)
            }
            filters.firstInstanceOrNull<TypeFilter>()?.let { addQueryParameter("type", it.selected) }
            filters.firstInstanceOrNull<DemographyFilter>()?.let { addQueryParameter("demography", it.selected) }
            filters.firstInstanceOrNull<StatusFilter>()?.let { addQueryParameter("status", it.selected) }
            filters.firstInstanceOrNull<GenreFilter>()?.let { genres ->
                genres.included.forEach { addQueryParameter("genders[]", it) }
                genres.excluded.forEach { addQueryParameter("exclude_genders[]", it) }
            }
            addQueryParameter("page", page.toString())
        }.build()

        val html = client.get(url, ajaxHeaders, Fetch.XHR).parseAs<LibraryPageDto>().html
        val document = Jsoup.parseBodyFragment(html, baseUrl)
        val mangas = document.select("#library-grid .element").mapNotNull(::mangaFromElement)
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("a[href*=/library/]") ?: return null
        val canonicalUrl = link.absUrl("href").toCanonicalMangaUrl()
        val type = canonicalUrl.mangaType()
        val title = link.selectFirst(".thumbnail-title h4")?.attrOrNull("title") ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(mangaIdentityUrl(title, type))
            this.title = title
            memo = memo.withCanonicalMangaUrl(canonicalUrl)
            thumbnail_url = link.selectFirst("img.cover-bg-img")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val sourceHost = baseUrl.toHttpUrl().host
        if (url.host.removePrefix("www.") != sourceHost.removePrefix("www.") ||
            url.pathSegments.firstOrNull() != "library"
        ) {
            return null
        }

        val canonicalUrl = url.toString().toCanonicalMangaUrl()
        return parseMangaDetails(client.get(url, Fetch.DOCUMENT).asJsoup()).apply {
            setUrlWithoutDomain(mangaIdentityUrl(title, canonicalUrl.mangaType()))
            memo = memo.withCanonicalMangaUrl(canonicalUrl)
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = manga
        .canonicalMangaUrl()
        ?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        ?: super.getMangaUrl(manga)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val identityUrl = baseUrl.toHttpUrl().resolve(manga.url)
        val canonicalUrl =
            manga.canonicalMangaUrl()
                ?: resolveLatestMangaUrl(
                    identityUrl?.queryParameter("title") ?: manga.title,
                    identityUrl?.queryParameter("type"),
                ).toCanonicalMangaUrl()
        val document = client.get(baseUrl.toHttpUrl().resolve(canonicalUrl)!!, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga =
            parseMangaDetails(document).apply {
                url = manga.url
                memo = manga.memo.withCanonicalMangaUrl(canonicalUrl)
            },
            chapters = parseChapterList(document),
        )
    }

    private fun SManga.canonicalMangaUrl(): String? = (memo[MANGA_URL_KEY] as? JsonPrimitive)
        ?.content
        ?.takeIf { it.startsWith("/library/") }
        ?: url.takeIf { it.startsWith("/library/") }

    private fun JsonObject.withCanonicalMangaUrl(url: String): JsonObject = JsonObject(this + (MANGA_URL_KEY to JsonPrimitive(url)))

    private fun String.toCanonicalMangaUrl(): String = baseUrl
        .toHttpUrl()
        .resolve(this)
        ?.encodedPath
        ?.takeIf { it.startsWith("/library/") }
        ?: throw Exception("Unsupported manga URL")

    private fun String.mangaType(): String? = baseUrl
        .toHttpUrl()
        .resolve(this)
        ?.pathSegments
        ?.getOrNull(1)

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.zt-hd__title")!!.text()
        thumbnail_url = document.selectFirst(".zt-hd__cover img")?.absUrl("src")
        author = document.select(".zt-hd__facts a[href*=filter_by=author]").joinToString { it.text() }.ifEmpty { null }
        artist = document.select(".zt-hd__facts a[href*=filter_by=artist]").joinToString { it.text() }.ifEmpty { null }
        genre = document.select(".zt-hd__kicker > :not(.zt-hd__state), a.zt-hd__genre")
            .map { element -> element.text().replaceFirstChar { it.uppercase() } }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when {
            document.selectFirst(".zt-hd__state--publishing") != null -> SManga.ONGOING
            document.selectFirst(".zt-hd__state--ended") != null -> SManga.COMPLETED
            document.selectFirst(".zt-hd__state--hiatus") != null -> SManga.ON_HIATUS
            document.selectFirst(".zt-hd__state--cancelled") != null -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val altTitles = document.selectFirst("p.zt-hd__alt")?.text()?.split("; ").orEmpty() +
            document.select("details.zt-hd__alts summary span, details.zt-hd__alts li").eachText()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#manga-synopsis"))
            .altTitles(altTitles.distinct().filter { it != title }, "Títulos alternativos")
            .build()
    }

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("li.upload-link")
        .flatMap { row ->
            val numberText = row.attr("data-chapter-number")
            val date =
                dateFormat.tryParseDate(
                    row
                        .selectFirst(".text-muted.small")
                        ?.text()
                        ?.substringAfterLast(" "),
                )

            row.select(".chapter-detail a[href*=/view_uploads/]").map { link ->
                SChapter.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    name = "Capítulo $numberText"
                    scanlator = link.parent()?.selectFirst("a[href*=/groups/]")?.text()
                    date_upload = date
                }
            }
        }.distinctBy { it.url }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get(getChapterUrl(chapter), Fetch.DOCUMENT)
        .asJsoup()
        .select("#reader-wrap img.reader-image")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client
        .get("$baseUrl/biblioteca", Fetch.DOCUMENT)
        .asJsoup()
        .select("label[for^=gender_]")
        .map { GenreDto(it.text(), it.attr("for").removePrefix("gender_")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SearchByFilter())
            add(SortFilter())
            add(TypeFilter())
            add(DemographyFilter())
            add(StatusFilter())
            data?.parseAs<List<GenreDto>>()?.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
        },
    )

    companion object {
        private const val MANGA_URL_KEY = "zonatmoorgunoriginal.mangaUrl"
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}
