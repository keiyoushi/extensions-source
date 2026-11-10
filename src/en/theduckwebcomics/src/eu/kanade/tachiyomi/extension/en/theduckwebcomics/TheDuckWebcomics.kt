package eu.kanade.tachiyomi.extension.en.theduckwebcomics

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class TheDuckWebcomics : KeiSource() {
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/search/?page=$page", Fetch.DOCUMENT).asJsoup()
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
        val document = client.get("$baseUrl/search/?page=$page&last_update=today", Fetch.DOCUMENT).asJsoup()
        return parseMangaList(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("page", page.toString())
            filters.filterIsInstance<QueryParam>().forEach {
                it.encode(this)
            }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
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
            val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()

            document.selectFirst(".yellow-box > .paranomargin")?.text()?.let(::error)
            document.chapters().mapIndexed { idx, pages ->
                SChapter.create().apply {
                    chapter_number = idx + 1f
                    name = pages.first().title
                    url = "${manga.url}${pages.first().id}/"
                }
            }.reversed()
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val (comic, id) = chapterUrl.toHttpUrl().pathSegments
        // A page of the last chapter, listed on its own before a newer chapter started
        val pages = document.chapters().firstOrNull { it.first().id == id }
            ?: return listOf(Page(0, imageUrl = document.pageImage()))

        return pages.mapIndexed { idx, page ->
            if (idx == 0) {
                Page(0, imageUrl = document.pageImage())
            } else {
                Page(idx, url = "$baseUrl/$comic/${page.id}/")
            }
        }
    }

    override suspend fun getImageUrl(page: Page): String = client.get(page.url, Fetch.DOCUMENT).asJsoup().pageImage()

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        ToneFilter(),
        StyleFilter(),
        GenreFilter(),
        RatingFilter(),
        UpdateFilter(),
    )

    private fun Document.pageImage(): String = selectFirst(".page-image")?.absUrl("src")
        ?: throw Exception("Page image not found")

    // The site's page list shows the first page of a chapter without a dash
    private fun Document.chapters(): List<List<ComicPage>> {
        val chapters = mutableListOf<MutableList<ComicPage>>()
        select("#page_dropdown > option").reversed().forEach { option ->
            val text = option.text()
            if (!text.startsWith("-") || chapters.isEmpty()) chapters.add(mutableListOf())
            chapters.last().add(ComicPage(option.attr("value"), text.removePrefix("- ")))
        }
        // The last chapter can still get new pages, so they stay separate to show up as new chapters
        return chapters.dropLast(1) + chapters.lastOrNull().orEmpty().map { listOf(it) }
    }
}

private class ComicPage(val id: String, val title: String)
