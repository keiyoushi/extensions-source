package eu.kanade.tachiyomi.extension.en.theduckwebcomics

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
abstract class TheDuckWebcomics : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/search/?page=$page").asJsoup()
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".breadcrumb ~ div[style]").map { element ->
            SManga.create().apply {
                val titleEl = element.selectFirst(".size24") ?: throw Exception("Title element not found")
                title = titleEl.text()
                setUrlWithoutDomain(titleEl.absUrl("href"))

                genre = element.selectFirst(".size10")?.text()?.substringBefore(",")
                description = element.selectFirst(".comicdescparagraphs")?.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                author = element.selectFirst(".size18")?.text()
                artist = author
            }
        }
        val hasNextPage = document.selectFirst("a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/search/?page=$page&last_update=today").asJsoup()
        return parseMangaList(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("page", page.toString())
            filters.filterIsInstance<QueryParam>().forEach {
                it.encode(this)
            }
        }.build()
        val document = client.get(url).asJsoup()
        return parseMangaList(document)
    }

    // The details are only available in search
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val manga = manga.apply { initialized = true }

        val chapters = if (fetchChapters) {
            val document = client.get(baseUrl + manga.url).asJsoup()

            document.selectFirst(".yellow-box > .paranomargin")?.text()?.let(::error)
            document.select("#page_dropdown > option").mapIndexed { idx, el ->
                SChapter.create().apply {
                    chapter_number = idx + 1f
                    name = el.text().substringAfter("- ")
                    setUrlWithoutDomain(el.absUrl("value") + "/")
                }
            }
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        val imageUrl = document.selectFirst(".page-image")?.absUrl("src")
            ?: throw Exception("Page image not found")
        return listOf(Page(0, imageUrl = imageUrl))
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        ToneFilter(),
        StyleFilter(),
        GenreFilter(),
        RatingFilter(),
        UpdateFilter(),
    )
}
