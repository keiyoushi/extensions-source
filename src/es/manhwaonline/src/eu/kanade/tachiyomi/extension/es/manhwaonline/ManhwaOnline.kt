package eu.kanade.tachiyomi.extension.es.manhwaonline

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
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

@Source
abstract class ManhwaOnline : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.POPULAR)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.LATEST)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            if (query.isNotBlank()) addQueryParameter("s", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.chapter-card").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("a.title")!!
                this.url = link.absUrl("href").toHttpUrl().encodedPath
                title = link.text()
                thumbnail_url = card.selectFirst("a.cover img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null

        return getMangaUpdate(
            manga = SManga.create().apply { this.url = "/manga/$slug/" },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    // Entries saved by the Madara version store the WordPress post id, with the path in memo
    private fun mangaPath(manga: SManga): String = if (manga.url.startsWith("/")) manga.url else manga.memo["path"]!!.string

    override fun getMangaUrl(manga: SManga): String = baseUrl + mangaPath(manga)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(manga, document), parseChapters(document))
    }

    private fun parseDetails(manga: SManga, document: Document): SManga = manga.apply {
        title = document.selectFirst("h1.comic-title")!!.text()
        thumbnail_url = document.selectFirst("img.manga-cover")?.absUrl("src")
        author = document.select(".hero-by a[href*=/manga-author/]").eachText().joinToString().ifEmpty { null }
        artist = document.select(".hero-by a[href*=/manga-artist/]").eachText().joinToString().ifEmpty { null }
        genre = (document.select(".hero-meta span[title=Tipo]").eachText() + document.select(".manga-genres a").eachText())
            .distinctBy(String::lowercase).joinToString().ifEmpty { null }
        status = when (document.selectFirst(".hero-status")?.ownText()?.substringBefore("·")?.trim()?.lowercase()) {
            "en emisión" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "en pausa" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val altTitles = document.selectFirst(".manga-alt")?.textOrNull()?.let { "Nombres alternativos: $it" }
        description = listOfNotNull(document.selectFirst(".synopsis-body")?.textOrNull(), altTitles)
            .joinToString("\n\n").ifEmpty { null }
    }

    private fun parseChapters(document: Document): List<SChapter> {
        // Old paths redirect, so keep the current one for chapter urls
        val mangaPath = document.location().toHttpUrl().encodedPath
        return document.select(".chapter-list-full .chapter-row-full").map { row ->
            SChapter.create().apply {
                url = row.attr("data-chapter-slug")
                name = row.selectFirst(".chapter-num")!!.text()
                date_upload = DATE_FORMAT.tryParseDate(row.selectFirst(".chapter-date")?.text())
                memo = buildJsonObject { put(MANGA_PATH, mangaPath) }
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        // Chapters saved before the Madara 1.6 migration store the full path
        if (chapter.url.startsWith("/")) return baseUrl + chapter.url
        return "$baseUrl${chapter.memo[MANGA_PATH]!!.string.trimEnd('/')}/${chapter.url}/"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".reader-pages img.reader-page")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        FieldFilter(),
        TypeFilter(),
        StatusFilter(),
        DemographicFilter(),
        Filter.Separator(),
        GenreFilter(),
        GenreModeFilter(),
        Filter.Separator(),
        YearFilter("Año desde", "year_min"),
        YearFilter("Año hasta", "year_max"),
        AdultFilter(),
    )

    companion object {
        private const val MANGA_PATH = "mangaPath"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy")
    }
}
