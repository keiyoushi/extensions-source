package eu.kanade.tachiyomi.extension.en.mangahen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SManga.Companion.COMPLETED
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class MangaHen : HttpSource() {

    override val name = "MangaHen"

    override val baseUrl = "https://manga-hen.com"

    private val advSearchURL = "$baseUrl/advanced-search"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private var tagsList: List<String> = listOf()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$advSearchURL/?search=1&type=0&sort=1&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()

        val mangas = doc.select("a[href^=/manga/]").map(::popularMangaFromElement)

        val hasNextPage = doc.select("a[href*=page]").any { it.text().isBlank() }

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst("h2")!!.ownText()
            setUrlWithoutDomain(element.absUrl("href"))
            thumbnail_url = element.selectFirst("img")!!.absUrl("src")
        }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$advSearchURL/?search=1&type=0&sort=2&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    private fun tagSearch(tag: String, tagsList: List<String>): String? {
        val index = (tagsList.indexOf(tag) + 1).toString()
        return if (index != "-1") index else null
    }

    private fun tagsList(): List<String> {
        if (tagsList.isEmpty()) {
            val request = GET(advSearchURL, headers)

            val response = client.newCall(request).execute()

            tagsList = response.asJsoup().select("li[onclick=updateTag(this)]").map { it.ownText().lowercase() }
        }
        return tagsList
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val includeTags = mutableListOf<String>()
        val excludeTags = mutableListOf<String>()

        val tagsList = tagsList()
        val url = advSearchURL.toHttpUrl().newBuilder().apply {
            filters.forEach {
                when (it) {
                    is SortFilter -> addQueryParameter("sort", it.getValue())

                    is TypeFilter -> addQueryParameter("type", it.getValue())

                    is TextFilter -> {
                        if (it.state.isNotEmpty()) {
                            it.state.split(",").filter(String::isNotBlank).map { tag ->
                                val trimmed = tag.trim().lowercase()
                                if (trimmed.startsWith('-')) {
                                    tagSearch(trimmed.removePrefix("-"), tagsList)?.let { tagInfo ->
                                        excludeTags.add(tagInfo)
                                    }
                                } else {
                                    tagSearch(trimmed, tagsList)?.let { tagInfo ->
                                        includeTags.add(tagInfo)
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            addQueryParameter("name", query)

            addQueryParameter("search", "1")
            if (includeTags.isNotEmpty()) addQueryParameter("include_tags", includeTags.joinToString())
            if (excludeTags.isNotEmpty()) addQueryParameter("exclude_tags", excludeTags.joinToString())
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val authors = document.select("a[href*=/circles/]").eachText().joinToString()
            val artists = document.select("a[href*=/authors/]").eachText().joinToString()
            val titles = document.select("h1.font-semibold").text().split(" | ")
            val altit = document.select("h2.text-lg.font-medium").text()
            initialized = true
            title = titles[0]
            author = authors.ifEmpty { artists }
            artist = artists
            genre = document.select("a[href*=/tags/]").eachText().joinToString()
            description = buildString {
                titles.getOrNull(1)?.let {
                    append("Alternative Titles: ", "\n", "- $it", "\n")
                    if (altit.isNotBlank()) append("- $altit", "\n")
                    append("\n")
                }
                append("Categories: ", document.select("a[href*=/categories/]").text(), "\n")
                append("Parodies: ", document.select("a[href*=/parodies/]").text(), "\n")
                append("Circles: ", document.select("a[href*=/circles/]").text(), "\n\n")
                append(document.select("tr:contains(page)").text(), "\n")
                append(document.select("tr:contains(view)").text(), "\n")
            }
            thumbnail_url = document.selectFirst("img[src*=thumbnail].w-96")?.absUrl("src")
            status = COMPLETED
        }
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    setUrlWithoutDomain(manga.url)
                },
            ),
        )
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val images = response.asJsoup().select("img[src*=images]:not(img[src*=thumbnail]).w-full, img[data-src*=images]")
        return images.mapIndexed { index, img ->
            val image = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
            Page(index, imageUrl = image.replace(Regex("-t(?=\\.)"), ""))
        }
    }

    override fun getFilterList() = getFilters()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
}
