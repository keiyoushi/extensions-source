package eu.kanade.tachiyomi.extension.en.mangahen

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SManga.Companion.COMPLETED
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class Gensura : KeiSource() {

    private val advSearchURL get() = "$baseUrl/advanced-search"

    private var tagsList: Map<String, String> = emptyMap()

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$advSearchURL/?search=1&type=0&sort=1&page=$page").toMangasPage()

    private fun Response.toMangasPage(): MangasPage {
        val doc = asJsoup()

        val mangas = doc.select("a[href^=/manga/]").map(::popularMangaFromElement)

        val hasNextPage = doc.select("a[href*=page]").any { it.text().isBlank() }

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2")!!.ownText()
        setUrlWithoutDomain(element.absUrl("href"))
        thumbnail_url = element.selectFirst("img")!!.absUrl("src")
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$advSearchURL/?search=1&type=0&sort=2&page=$page").toMangasPage()

    // Search

    private suspend fun tagsList(): Map<String, String> {
        if (tagsList.isEmpty()) {
            tagsList = client.get("$advSearchURL/").asJsoup().select("li[onclick=updateTag(this)]")
                .associate { it.ownText().lowercase() to it.attr("data-value") }
        }
        return tagsList
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val includeTags = mutableListOf<String>()
        val excludeTags = mutableListOf<String>()

        val tagsList = tagsList()
        // Without the trailing slash the site ignores all search parameters
        val url = "$advSearchURL/".toHttpUrl().newBuilder().apply {
            filters.forEach {
                when (it) {
                    is SortFilter -> addQueryParameter("sort", it.getValue())

                    is TypeFilter -> addQueryParameter("type", it.getValue())

                    is TextFilter -> {
                        if (it.state.isNotEmpty()) {
                            it.state.split(",").filter(String::isNotBlank).map { tag ->
                                val trimmed = tag.trim().lowercase()
                                if (trimmed.startsWith('-')) {
                                    tagsList[trimmed.removePrefix("-").trim()]?.let { tagInfo ->
                                        excludeTags.add(tagInfo)
                                    }
                                } else {
                                    tagsList[trimmed]?.let { tagInfo ->
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
            if (includeTags.isNotEmpty()) addQueryParameter("include_tags", includeTags.joinToString(","))
            if (excludeTags.isNotEmpty()) addQueryParameter("exclude_tags", excludeTags.joinToString(","))
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return client.get(url).toMangasPage()
    }

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) mangaDetails(manga) else manga

        val chapterList = listOf(
            SChapter.create().apply {
                name = "Chapter"
                setUrlWithoutDomain(manga.url)
            },
        )

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun mangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SManga.create().apply {
            val authors = document.select("a[href*=/circles/]").eachText().joinToString()
            val artists = document.select("a[href*=/authors/]").eachText().joinToString()
            val titles = document.select("h1.font-semibold").text().split(" | ")
            val altit = document.select("h2.text-lg.font-medium").text()
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

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = client.get(getChapterUrl(chapter)).asJsoup().select("img[src*=images]:not(img[src*=thumbnail]).w-full, img[data-src*=images]")
        return images.mapIndexed { index, img ->
            val image = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
            Page(index, imageUrl = image.replace(Regex("-t(?=\\.)"), ""))
        }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()
}
