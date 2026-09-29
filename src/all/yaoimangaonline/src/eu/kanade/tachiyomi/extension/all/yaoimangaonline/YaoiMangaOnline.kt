package eu.kanade.tachiyomi.extension.all.yaoimangaonline

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class YaoiMangaOnline : KeiSource() {

    override val supportsLatest = false

    // =================== Popular ===================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/page/$page/").asJsoup())

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".post:not(.sticky):not(.category-gay-movies):not(.category-yaoi-anime) > div > a")
            .map { element ->
                SManga.create().apply {
                    title = element.attr("title")
                    setUrlWithoutDomain(element.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.attr("src")
                }
            }
        val hasNextPage = document.selectFirst(".herald-pagination > .next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =================== Latest ===================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =================== Search ===================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            filters.forEach {
                when (it) {
                    is CategoryFilter -> if (it.state != 0) {
                        addQueryParameter("cat", it.toString())
                    }
                    is TagFilter -> if (it.state != 0) {
                        addEncodedPathSegments("tag/$it")
                    }
                    else -> {}
                }
            }
            addEncodedPathSegments("page/$page")
            addQueryParameter("s", query)
        }.build()

        return parseMangasPage(client.get(url).asJsoup())
    }

    // =================== Details ===================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1.entry-title").text()
            .substringBeforeLast("by").trim()
        thumbnail_url = document.selectFirst(".herald-post-thumbnail img")?.attr("src")
        description = document
            .select(".entry-content > p:not(:has(img)):not(:contains(You need to login))")
            .joinToString("\n\n") { it.wholeText() }
        genre = document.select(".meta-tags > a").joinToString { it.text() }
        author = document.select(".entry-content > p:contains(Mangaka:)").text()
            .substringAfter("Mangaka:")
            .substringBefore("Language:")
            .trim()
    }

    // =================== Chapters ===================

    private fun chapterListParse(document: Document): List<SChapter> {
        val chapters = document.select(".mpp-toc a").map { element ->
            SChapter.create().apply {
                name = element.ownText()
                setUrlWithoutDomain(element.absUrl("href").ifEmpty { element.baseUri() })
            }
        }
        return chapters.ifEmpty {
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = document.location().toHttpUrl().encodedPath
                },
            )
        }.reversed()
    }

    // =================== Pages ===================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(baseUrl + chapter.url).asJsoup()
        .select(".entry-content img")
        .mapIndexed { idx, img -> Page(idx, imageUrl = img.attr("src")) }

    override fun getFilterList(data: JsonElement?) = FilterList(CategoryFilter(), TagFilter())
}
