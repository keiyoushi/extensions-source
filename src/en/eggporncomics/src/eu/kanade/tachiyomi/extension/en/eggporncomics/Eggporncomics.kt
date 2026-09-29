package eu.kanade.tachiyomi.extension.en.eggporncomics

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
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZonedDateTime

@Source
abstract class Eggporncomics : KeiSource() {

    // Popular

    // couldn't find a page with popular comics, defaulting to the popular "anime-comics" category
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/category/1/anime-comics?page=$page").asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.preview:has(div.name)").map { element ->
            SManga.create().apply {
                element.selectFirst("a:has(img)")?.let { a ->
                    setUrlWithoutDomain(a.absUrl("href"))
                    title = a.text()
                    thumbnail_url = a.selectFirst("img")?.absUrl("src")
                }
            }
        }
        val hasNextPage = document.selectFirst("ul.ne-pe li.next:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/latest-comics?page=$page").asJsoup())

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search/${query.replace(queryRegex, "-")}?page=$page".toHttpUrl()
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()
            val category = filters.firstInstanceOrNull<CategoryFilter>()
            val comics = filters.firstInstanceOrNull<ComicsFilter>()

            when {
                category?.isNotNull() == true && comics?.isNotNull() == true -> {
                    url.addPathSegments("category-tag/${category.toUriPart()}/${comics.toUriPart()}")
                }
                category?.isNotNull() == true -> {
                    url.addPathSegments("category/${category.toUriPart()}")
                }
                comics?.isNotNull() == true -> {
                    url.addPathSegments("comics-tag/${comics.toUriPart()}")
                }
            }

            url.addQueryParameter("page", page.toString())
            url.build()
        }

        val response = client.get(url, ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            // when combining a category filter and comics filter, if there are no results the source
            // issues a 404, override that so as not to confuse users
            if (response.request.url.toString().contains("category-tag") && response.code == 404) {
                return MangasPage(emptyList(), false)
            }
            throw Exception("HTTP error ${response.code}")
        }

        return parseMangaList(response.asJsoup())
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val chapterUrl = response.request.url.toString()
        val document = response.asJsoup()

        manga.apply {
            thumbnail_url = document.selectFirst("div.grid div.image img")?.toFullSizeImage()
            description = document.select("div.links ul").joinToString("\n") { element ->
                element.select("a").joinToString(
                    prefix = element.select("span").text().replace(descriptionPrefixRegex, ": "),
                ) { it.text() }
            }
        }

        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(chapterUrl)
            name = "Chapter"
            date_upload = document.selectFirst("div.info > div.meta li:contains(days ago)")
                ?.let {
                    val days = it.text().substringBefore(" ").toLongOrNull() ?: 0L
                    ZonedDateTime.now().minusDays(days).toInstant().toEpochMilli()
                }
                ?: 0L
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    // Pages

    private fun Element.toFullSizeImage() = absUrl("src").replace("thumb300_", "")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.grid div.image img").mapIndexed { i, img ->
            Page(i, imageUrl = img.toFullSizeImage())
        }
    }

    // Filters

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Leave query blank to use filters"),
        Filter.Separator(),
        CategoryFilter("Category", getCategoryList),
        ComicsFilter("Comics", getComicsList),
    )

    companion object {
        private val queryRegex = Regex("""[\s']""")
        private val descriptionPrefixRegex = Regex(""":.*""")
    }
}
