package eu.kanade.tachiyomi.extension.en.artlapsa

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

@Source
abstract class ArtLapsa : Keyoapp() {

    override suspend fun requestGeneres() = client.get("$baseUrl/search")

    override fun parseGenres(document: Document) = document.select("[wire:model.live=genre] option:not(:contains(All))").associate {
        it.text() to it.attr("value")
    }

    override fun searchUrlBuilder(query: String, page: Int) = "$baseUrl/search".toHttpUrl().newBuilder().apply {
        if (page > 1) addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            addQueryParameter("title", query)
        }
    }

    override fun searchMangaSelector() = "main#main-content [wire:key*='serie']"

    override fun parseSearchManga(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }

    override val altNameSelector: String = "div.font-medium:containsOwn(Alternative titles) ~ div span.select-all"
    override val statusSelector = "[alt=Status]"
    override val typeSelector = "[alt=Type]"

    override val paidChapterSelector = "img[alt~=Coin], img[src*=star-circle]"

    override fun pageListParse(document: Document): List<Page> {
        val xData = document.selectFirst("[x-data^=immersiveReader]")!!.attr("x-data")
        val pagesJs = xData.substringAfter("JSON.parse('", "").substringBefore("')")
        if (pagesJs.isEmpty()) throw Exception("Log in via WebView and purchase this chapter to read.")

        val pagesJson = "\"$pagesJs\"".parseAs<String>()
        return pagesJson.parseAs<List<PageDto>>().mapIndexed { i, page ->
            Page(i, imageUrl = page.path)
        }
    }
}

@Serializable
private class PageDto(
    val path: String,
)
