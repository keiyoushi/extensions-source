package eu.kanade.tachiyomi.extension.zh.mh1234

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MH1234 : KeiSource() {

    // Popular Page

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("order")
            addPathSegment("hits")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        return mangaListParse(client.get(url))
    }

    // Latest Page

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("order")
            addPathSegment("addtime")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return mangaListParse(client.get(url))
    }

    // Search Page

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return parseMangaDetails(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = if (query.isNotBlank()) {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addPathSegment(query)
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        mangaListParse(client.get(url))
    } else {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected?.second ?: "0"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected?.second ?: "0"
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected?.second ?: "id"

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("tags")
            addPathSegment(genre)
            addPathSegment("finish")
            addPathSegment(status)
            addPathSegment("order")
            addPathSegment(sort)
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        mangaListParse(client.get(url))
    }

    // Shared manga list parsing

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(MANGA_LIST_SELECTOR).map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.comic-card__link")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.selectFirst(".comic-card__title")!!.text()
            thumbnail_url = it.selectFirst("img.comic-card__image")?.absUrl("data-src")
        }
    }

    // Manga Detail + Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        val info = document.select(".mint-work-info p")

        title = document.selectFirst("#mintWorkTitle")?.text().orEmpty()
        author = info.getOrNull(0)?.text()?.removeSuffix(" 著")
        genre = info.getOrNull(1)?.ownText()?.split(" ")?.joinToString()
        status = when (info.getOrNull(1)?.selectFirst(".mint-tag")?.text()) {
            "连载" -> SManga.ONGOING
            "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.selectFirst("#mintIntroPanel > div")?.text()

        setUrlWithoutDomain(document.location())
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select(".mint-chapter-grid a")
        return chapters.mapIndexedNotNull { index, element ->
            val title = element.ownText()
            if (title.contains("APP")) return@mapIndexedNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = title
                chapter_number = (chapters.size - index).toFloat()
            }
        }
    }

    // Related mangas

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val doc = client.get(getMangaUrl(manga)).asJsoup()

        return doc.select(".mint-section:has(h2:contains(喜欢这部的也在看)) .mint-cover-card").mapNotNull {
            val name = it.selectFirst("h3")?.text()?.takeIf(String::isNotBlank) ?: return@mapNotNull null

            SManga.create().apply {
                title = name
                setUrlWithoutDomain(it.absUrl("href"))
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
    }

    // Manga View Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var response = client.get(getChapterUrl(chapter))
        // Handle redirection to reader in JS
        val url = REPLACE_URL_REGEX.find(response.peekBody(10240).string())?.groupValues?.get(1)
        if (url != null) {
            response.close()
            response = client.get(url)
        }

        val document = response.asJsoup()
        return document.select("img.reader-image").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("data-src"))
        }
    }

    // Filters

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val doc = client.get("$baseUrl/category/").asJsoup()

        return doc.select("a.filter-tag").associate {
            it.text() to it.attr("href").substringAfterLast("/")
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            val genres = data?.parseAs<Map<String, String>>()
            genres?.let { add(GenreFilter(genres)) }
            add(StatusFilter())
            add(SortFilter())
        },
    )

    companion object {
        private val REPLACE_URL_REGEX = """replace\(["'](https://.*?)["']""".toRegex()
        private const val MANGA_LIST_SELECTOR = ".comic-card"
        private const val NEXT_PAGE_SELECTOR = ".pagination-wrapper a:contains(下一页), .pagination-wrapper a:contains(>)"
    }
}
