package eu.kanade.tachiyomi.extension.all.everiaclubcom

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
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

@Source
abstract class EveriaClubCom : KeiSource() {

    private val Element.imgSrc: String?
        get() = when {
            hasAttr("data-original") -> attr("data-original")
            hasAttr("data-lazy-src") -> attr("data-lazy-src")
            hasAttr("data-src") -> attr("data-src")
            hasAttr("src") -> attr("src")
            else -> null
        }

    private fun mangaFromElement(it: Element) = SManga.create().apply {
        setUrlWithoutDomain(it.attr("abs:href").removePrefix(baseUrl))
        with(it.selectFirst("img")!!) {
            thumbnail_url = imgSrc
            title = Parser.unescapeEntities(attr("title"), false)
        }
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/?page=$page").asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".mainleft .leftp > a").map {
            mangaFromElement(it)
        }
        val isLastPage = document.selectFirst("li:has(span.current) + li > a")
        return MangasPage(mangas, isLastPage != null)
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".mainright li a").map {
            mangaFromElement(it)
        }
        return MangasPage(mangas, false)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tagFilter = filters.firstInstance<TagFilter>()
        val categoryFilter = filters.firstInstance<CategoryFilter>()
        val url = when {
            tagFilter.state.isNotBlank() -> baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("tags")
                .addPathSegment(tagFilter.state)
                .addPathSegment(page.toString())

            categoryFilter.state != 0 -> "$baseUrl/${categoryFilter.toUriPart()}?page=$page".toHttpUrl().newBuilder()

            query.isNotBlank() -> baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment("")
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", page.toString())

            else -> "$baseUrl/?page=$page".toHttpUrl().newBuilder()
        }
        return parseMangaList(client.get(url.build()).asJsoup())
    }

    // Details
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (fetchDetails) {
            val document = client.get(getMangaUrl(manga)).asJsoup()
            manga.apply {
                genre = document.select("div.end span:contains(Tags:) ~ a > p.tags").joinToString {
                    it.ownText()
                }
                status = SManga.COMPLETED
            }
        }

        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            chapter_number = 1f
            date_upload = 0L
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val images = document.select(".mainleft img")
        return images.mapIndexed { index, image ->
            Page(index, imageUrl = image.imgSrc)
        }
    }

    // Filters
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        TagFilter(),
        CategoryFilter(),
    )

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<Pair<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
        fun toUriPart() = valuePair[state].second
    }

    class CategoryFilter :
        UriPartFilter(
            "Category",
            arrayOf(
                Pair("Any", ""),
                Pair("Gravure", "Gravure.html"),
                Pair("Japan", "Japan.html"),
                Pair("Korea", "Korea.html"),
                Pair("Thailand", "Thailand.html"),
                Pair("Chinese", "Chinese.html"),
                Pair("Cosplay", "Cosplay.html"),
            ),
        )

    class TagFilter : Filter.Text("Tag")
}
