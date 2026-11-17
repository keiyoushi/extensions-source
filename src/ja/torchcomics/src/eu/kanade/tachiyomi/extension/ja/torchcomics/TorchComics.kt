package eu.kanade.tachiyomi.extension.ja.torchcomics

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.textOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class TorchComics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = fetchListing(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return MangasPage(emptyList(), false)

        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("post_type", "product")
            .addQueryParameter("s", query)
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseMangas(document), false)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments
        return when {
            segments.size >= 2 && segments[0] == "product" -> {
                val manga = fetchManga(segments[1])
                MangasPage(listOfNotNull(manga), false)
            }
            segments.firstOrNull() == "product" -> fetchListing(page)
            else -> MangasPage(emptyList(), false)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/product/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/story/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        _chapters: List<SChapter>,
        _fetchDetails: Boolean,
        _fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(manga, document),
            chapters = parseChapters(document),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".manga_page:not(.info_page) span[img-url]")
            .mapNotNull { element ->
                val raw = element.attrOrNull("abs:img-url") ?: return@mapNotNull null
                raw.replaceFirst("http://", "https://").takeUnless(::isAdvertisement)
            }
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    /**
     * `/product` is ordered by the site's own `UPDATE` date, newest first. The first screen
     * holds 20 works; the theme's "load more" endpoint serves 10 per `_page`, and `_page`
     * counts those tens — so the second screen is `_page=3`, not 2.
     */
    private suspend fun fetchListing(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/product" else "$baseUrl/product_paging?_page=${page + 1}"
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = parseMangas(document)
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private suspend fun fetchManga(slug: String): SManga? {
        val document = client.get("$baseUrl/product/$slug", Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".work_detail") == null) return null
        return parseDetails(SManga.create().apply { url = slug }, document)
    }

    private fun parseMangas(document: Document): List<SManga> = document.select("article").mapNotNull { element ->
        val anchor = element.selectFirst("a[href*='/product/']") ?: return@mapNotNull null
        val slug = anchor.attr("abs:href").substringAfterLast("/product/").trim('/')
        val title = element.selectFirst("h3")?.text()
            ?.removeSurrounding("「", "」")
            ?: throw IllegalStateException("Missing title")
        if (slug.isBlank() || slug == STORE_INFO_SLUG) return@mapNotNull null

        SManga.create().apply {
            url = slug
            this.title = title
            thumbnail_url = element.selectFirst("img")?.attrOrNull("abs:src")
            description = element.select("p").lastOrNull()?.text()
        }
    }

    private fun parseDetails(manga: SManga, document: Document): SManga {
        val header = document.selectFirst(".work_detail header")

        return manga.apply {
            header?.selectFirst("h3")?.text()
                ?.removeSurrounding("「", "」")
                ?.takeIf { it.isNotBlank() }
                ?.let { title = it }

            header?.selectFirst("img")?.attrOrNull("abs:src")
                ?.let { thumbnail_url = it }

            document.selectFirst(".profile p.name")?.textOrNull()
                ?.let { author = it }

            document.selectFirst(".work_detail > .description")?.let { element ->
                MarkdownDescription.Builder()
                    .description(element)
                    .build()
                    ?.let { description = it }
            }

            status = SManga.ONGOING
            initialized = true
        }
    }

    /** The site lists episodes oldest-first; the app wants newest-first. */
    private fun parseChapters(document: Document): List<SChapter> {
        val episodes = document.select(".episode ul li a")
        if (episodes.isEmpty()) return parseSingleChapter(document)

        return episodes
            .map { element ->
                val slug = element.attr("abs:href").substringAfterLast("/story/").trim('/')
                    .takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Missing chapter slug")
                val name = element.selectFirst("span")?.textOrNull()
                    ?: throw IllegalStateException("Missing chapter name")

                SChapter.create().apply {
                    url = slug
                    this.name = name
                    chapter_number = CHAPTER_NUMBER.find(name)?.value?.toFloatOrNull() ?: 0f
                    date_upload = 0L
                }
            }
            .reversed()
    }

    /** One-shots have no episode list; their only page is linked from the cover. */
    private fun parseSingleChapter(document: Document): List<SChapter> {
        val header = document.selectFirst(".work_detail header") ?: return emptyList()
        val href = header.selectFirst("p.cover a")?.attr("abs:href")
            ?.takeIf { it.contains("/story/") }
            ?: return emptyList()

        val name = header.selectFirst("h3")?.text()
            ?.removeSurrounding("「", "」")
            ?.takeIf { it.isNotBlank() }
            ?: "1"

        return listOf(
            SChapter.create().apply {
                url = href.substringAfterLast("/story/").trim('/')
                this.name = name
                chapter_number = 1f
                date_upload = 0L
            },
        )
    }

    /** The viewer occasionally prepends a promo image whose file name starts with `AD`. */
    private fun isAdvertisement(url: String): Boolean = url.substringAfterLast('/').startsWith("AD")

    companion object {
        private val CHAPTER_NUMBER = Regex("\\d+")
        private const val STORE_INFO_SLUG = "store_information"
    }
}
