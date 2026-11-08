package eu.kanade.tachiyomi.extension.tr.mangamac

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
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class MangaMAC : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getCatalog(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getCatalog(page, sort = "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getCatalog(
        page,
        sort = (filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()).toUriPart(),
        catalogs = (filters.firstInstanceOrNull<TypeFilter>() ?: TypeFilter()).catalogs(),
        query = query.trim(),
        genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart().orEmpty(),
        status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty(),
    )

    // The site keeps manga and webtoons in two catalogs with the same filters: each page lists both, manga first.
    private suspend fun getCatalog(
        page: Int,
        sort: String,
        catalogs: List<String> = TypeFilter().catalogs(),
        query: String = "",
        genre: String = "",
        status: String = "",
    ): MangasPage = coroutineScope {
        val documents = catalogs.map { catalog ->
            async {
                val url = "$baseUrl/$catalog".toHttpUrl().newBuilder().apply {
                    if (query.isNotEmpty()) addQueryParameter("search", query)
                    if (genre.isNotEmpty()) addQueryParameter("genre", genre)
                    if (status.isNotEmpty()) addQueryParameter("status", status)
                    addQueryParameter("sort", sort)
                    addQueryParameter("page", page.toString())
                }.build()
                client.get(url, Fetch.DOCUMENT).asJsoup()
            }
        }.awaitAll()

        MangasPage(
            documents.flatMap { document -> document.select("article.bw-card, article.result-card").map { it.toSManga() } },
            documents.any { it.selectFirst(".bw-pagination .fa-chevron-right, .pagination .fa-chevron-right") != null },
        )
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst("h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val slug = segments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val path = when (segments[0]) {
            "manga", "webtoon" -> "/${segments[0]}/$slug"
            // Chapter links are /oku/<slug>/bolum-1, or /oku/<slug>-bolum-1 in the site's own lists.
            "oku" -> "/manga/" + if (segments.size > 2) slug else slug.substringBeforeLast("-bolum-")
            "oku-webtoon" -> "/webtoon/$slug"
            else -> return null
        }
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.parseDetails(manga.url), document.parseChapters())
    }

    private fun Document.parseDetails(url: String) = SManga.create().apply {
        this.url = url
        title = selectFirst("h1.manga-title-minimal")!!.text()
        thumbnail_url = selectFirst(".manga-cover-minimal img")?.absUrl("src")
        author = metaItem("fa-pen")
        artist = metaItem("fa-palette")
        status = parseStatus(metaItem("fa-info-circle"))
        genre = select(".manga-genres-minimal a").joinToString { it.text() }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst(".manga-description-minimal"))
            .field("Kaynak", selectFirst(".manga-source-box .source-name")?.text())
            .build()
    }

    private fun Document.metaItem(icon: String) = selectFirst(".manga-meta-item:has(i.$icon)")?.textOrNull()

    // Manga pages show the status in the site's interface language (English or Turkish), webtoon pages in Turkish.
    private fun parseStatus(status: String?): Int {
        val text = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "ongoing" in text || "devam" in text -> SManga.ONGOING
            "completed" in text || "tamamlan" in text -> SManga.COMPLETED
            "hiatus" in text || text.startsWith("ara") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // The site lists chapters from the first to the last.
    private fun Document.parseChapters() = select("#chaptersList a.chapter-item-minimal").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("h4")!!.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".chapter-meta-minimal span:has(.fa-calendar)")?.text())
        }
    }.reversed()

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".pages-container .reader-page img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Istanbul"))
    }
}
