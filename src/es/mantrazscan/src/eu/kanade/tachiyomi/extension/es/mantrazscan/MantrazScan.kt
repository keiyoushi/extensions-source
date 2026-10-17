package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class MantrazScan : KeiSource() {

    override fun Headers.Builder.configureHeaders() = apply {
        add(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page)

    // The site's only listing is sorted by latest update, so Popular already shows the latest chapters; hide the duplicate Latest tab
    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val urlBuilder = exploreUrl(page).toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query.trim())
        }

        filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.let { genre ->
            urlBuilder.addQueryParameter("genero", genre)
        }

        return parseMangaPage(client.get(urlBuilder.build()).asJsoup(), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/manga/") || url.encodedPath.contains("/capitulo-")) {
            return null
        }

        val document = client.get(url).asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain(url.toString())
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        return IMAGE_REGEX.findAll(document.html())
            .map { it.value }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
            .toList()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filtrar por género"),
        GenreFilter(),
    )

    private fun exploreUrl(page: Int) = if (page == 1) "$baseUrl/explorar/" else "$baseUrl/explorar/page/$page/"

    private suspend fun getMangaList(page: Int): MangasPage = parseMangaPage(client.get(exploreUrl(page)).asJsoup(), page)

    private fun parseMangaPage(document: Document, page: Int): MangasPage {
        val mangas = document.select("div.s-card")
            .mapNotNull { element ->
                val url = element.selectFirst("a.s-card-imglink")
                    ?.attr("href")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val title = element.selectFirst("a.s-card-title")
                    ?.text()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(url)
                    this.title = title
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }
            .distinctBy { it.url }

        return MangasPage(
            mangas = mangas,
            hasNextPage = document.selectFirst(
                "a[href*=\"/explorar/page/${page + 1}/\"]",
            ) != null,
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()

        thumbnail_url = document.selectFirst(".series-cover img")?.absUrl("src")

        genre = document.select("a.genre-tag")
            .map { it.text() }
            .filter { it.isNotBlank() }
            .joinToString()

        description = document.selectFirst(".series-desc")
            ?.text()
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val dates = document.extractNextJs<ChapterDatesDto>()?.chapterDates.orEmpty()

        return document.select("a.ch-row")
            .mapNotNull { element ->
                val url = element.attr("href")
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val number = CHAPTER_NUMBER_REGEX.find(url)
                    ?.groupValues
                    ?.get(1)
                    ?: return@mapNotNull null

                SChapter.create().apply {
                    setUrlWithoutDomain(url)
                    name = "Capítulo $number"
                    chapter_number = number.toFloatOrNull() ?: -1f
                    date_upload = dates[number]?.times(1000) ?: 0L
                }
            }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    companion object {
        private val CHAPTER_NUMBER_REGEX = Regex("""capitulo-([0-9]+(?:\.[0-9]+)?)""")

        private val IMAGE_REGEX = Regex(
            """https://[^"\\ ]+/WP-manga/[^"\\ ]+\.(?:webp|WEBP|jpg|JPG|jpeg|JPEG|png|PNG)""",
        )
    }
}
