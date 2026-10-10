package eu.kanade.tachiyomi.extension.en.kitsunedawn

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class KitsuneDawn : Keyoapp() {

    // The home page only renders a fixed top-N list, so use the paginated
    // search listing sorted by popularity instead.
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()

        return parseSearchManga(client.get(url))
    }

    override suspend fun getLatestUpdates(page: Int) = latestUpdatesParse(client.get("$baseUrl/latest?page=$page").asJsoup())

    override fun latestUpdatesNextPageSelector() = "a[href*='?page=']"

    override suspend fun requestGeneres() = client.get("$baseUrl/search")

    override fun parseGenres(document: Document) = document.select("[wire:model.live=genre] option:not(:contains(All))").associate {
        it.text() to it.attr("value")
    }

    override fun searchUrlBuilder(query: String, page: Int) = "$baseUrl/search".toHttpUrl().newBuilder().apply {
        if (page > 1) addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            // The site answers 404 to title queries longer than 100 characters.
            addQueryParameter("title", query.take(100))
        }
    }

    override fun searchMangaSelector() = "main#main-content [wire:key*='serie']"

    override fun parseSearchManga(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }

    override val altNameSelector: String = "details[data-testid=series-other-names] li.select-all"
    override val statusSelector = "a[aria-label=Status]"
    override val typeSelector = "a[aria-label=Type]"
    override val genreSelector = "div:has(>h1) a[href*='/genres/']"
    override val authorSelector = "dt:contains(Author) + dd"
    override val artistSelector = "dt:contains(Artist) + dd"

    override val paidChapterSelector = "img[alt~=Coin], img[src*=star-circle]"

    override fun pageListParse(document: Document): List<Page> {
        val xData = document.selectFirst("[x-data^=immersiveReader]")?.attr("x-data") ?: return emptyList()
        val pagesJs = xData.substringAfter("JSON.parse('", "").substringBefore("')")
        if (pagesJs.isEmpty()) return emptyList()

        // Wrapping in quotes unescapes the JS string literals into valid JSON.
        val pagesJson = "\"$pagesJs\"".parseAs<String>()
        return pagesJson.parseAs<List<PageDto>>().mapIndexed { i, page ->
            Page(i, imageUrl = page.path)
        }
    }

    // Covers are plain <img> tags since the site redesign.
    override fun Element.getImageUrl(selector: String): String? = selectFirst("img[alt$=' cover']")?.attr("abs:src")
}

@Serializable
private class PageDto(
    val path: String,
)
