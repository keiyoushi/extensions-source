package eu.kanade.tachiyomi.extension.en.mangapill

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.util.Locale

@Source
abstract class MangaPill : KeiSource() {

    // Popular fetches the homepage where the "Trending Mangas" section is
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/").asJsoup()
        val mangas = document.select("div:has(h4:contains(Trending)) > .grid > div:not([class])").map { element ->
            latestUpdatesFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    // Latest fetches the /chapters url
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/chapters").asJsoup()
        val mangas = document.select(".grid > div:not([class])").map { element ->
            latestUpdatesFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("data-src")
        setUrlWithoutDomain(element.selectFirst("a[href^='/manga/']")!!.absUrl("href"))
        title = element.selectFirst("div.line-clamp-2")!!.text()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create()
        details.title = manga.title
        details.author = ""
        details.artist = ""
        val genres = mutableListOf<String>()
        document.select("a[href*=genre]").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        details.genre = genres.joinToString(", ")
        details.status = parseStatus(document.select("div.container > div:first-child > div:last-child > div:nth-child(3) > div:nth-child(2) > div").text())
        details.description = document.select("div.container > div:first-child > div:last-child > div:nth-child(2) > p").text()
        details.thumbnail_url = document.select("div.container > div:first-child > div:first-child > img").first()!!.attr("data-src")

        val chapterList = document.select("#chapters > div > a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
                date_upload = 0
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase(Locale.ENGLISH).contains("publishing") -> SManga.ONGOING
        element.lowercase(Locale.ENGLISH).contains("finished") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("picture img").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("data-src"))
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("genre", genre)
                        }
                    }
                }

                is Status -> url.addQueryParameter("status", filter.toUriPart())

                is Type -> url.addQueryParameter("type", filter.toUriPart())

                else -> {}
            }
        }

        val document = client.get(url.build()).asJsoup()
        val mangas = document.select(".grid > div:not([class])").map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = document.selectFirst("a.btn.btn-sm") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        Status(),
        Type(),
        GenreList(getGenreList()),
    )
}
