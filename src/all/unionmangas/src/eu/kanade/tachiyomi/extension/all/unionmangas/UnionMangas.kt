package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class UnionMangas(
    override val lang: String,
    private val siteLang: String,
) : ParsedHttpSource() {

    override val name: String = "Union Mangas"

    override val baseUrl: String = "https://unionmangas.xyz"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(LangGroupFilter(getLangFilter()))

    override fun chapterFromElement(element: Element): SChapter {
        TODO("Not yet implemented")
    }

    override fun chapterListSelector(): String {
        TODO("Not yet implemented")
    }

    override fun imageUrlParse(document: Document): String {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.htmlDocumentToDto()
        return MangasPage(
            mangas = dto.toLatestUpdatesModel(),
            hasNextPage = dto.hasNextPageToLatestUpdates(),
        )
    }

    override fun latestUpdatesNextPageSelector() = "#next-prev a:nth-child(2):not(.line-through)"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/$siteLang/latest-releases".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = "main > div > table > tbody > tr"

    override fun mangaDetailsParse(response: Response) =
        response.htmlDocumentToDto().toMangaDetailsModel()

    override fun pageListParse(document: Document): List<Page> {
        TODO("Not yet implemented")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.htmlDocumentToDto()
        return MangasPage(
            mangas = dto.toPopularMangaModel(),
            hasNextPage = dto.hasNextPageToPopularMangas(),
        )
    }

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$siteLang")

    override fun popularMangaSelector() = "main > div.pt-1 > a"

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaSelector(): String {
        TODO("Not yet implemented")
    }

    private fun getLangFilter() = listOf(
        CheckboxFilterOption("it", "Italian"),
        CheckboxFilterOption("pt_br", "Portuguese (Brazil)"),

    ).filterNot { it.value == siteLang }

    private fun Response.htmlDocumentToDto(): UnionMangasDto {
        val jsonContent = asJsoup().selectFirst("script#__NEXT_DATA__")!!.html()
        return json.decodeFromString<UnionMangasDto>(jsonContent)
    }
    companion object {
        val apiUrl = "https://api.unionmanga.xyz"
    }
}

class CheckboxFilterOption(val value: String, name: String, default: Boolean = false) : Filter.CheckBox(name, default)

abstract class CheckboxGroupFilter(name: String, options: List<CheckboxFilterOption>) : Filter.Group<CheckboxFilterOption>(name, options) {
    val selected: List<String>
        get() = state.filter { it.state }.map { it.value }
}

class LangGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Languages", options)
