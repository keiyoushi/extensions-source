package eu.kanade.tachiyomi.extension.en.manhwaz

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Manhwaz : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this.rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage {
        // The homepage "popular" grid is static across ?page=N, so only the first page is useful.
        if (page > 1) {
            return MangasPage(emptyList(), false)
        }
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("article.popular-card").map(::cardToManga)
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/?page=$page").asJsoup()
        val mangas = document.select("article.latest-card").map(::cardToManga)
        return MangasPage(mangas, document.hasNextPage())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreId = filters.firstInstanceOrNull<GenreFilter>()?.selected?.id.orEmpty()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("s", query.trim())
            } else {
                when (genreId) {
                    "completed" -> addPathSegment("completed")
                    "" -> Unit
                    else -> addPathSegments(genreId)
                }
            }
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        val selector = if (query.isBlank() && genreId.isEmpty()) {
            "article.latest-card"
        } else {
            "article.popular-card"
        }
        val mangas = document.select(selector).map(::cardToManga)
        return MangasPage(mangas, document.hasNextPage())
    }

    private fun cardToManga(element: Element): SManga = SManga.create().apply {
        element.selectFirst("h3 a")!!.also {
            title = it.text().trim()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.selectFirst("img")?.let {
            it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }
        }
    }

    private fun Document.hasNextPage(): Boolean = selectFirst("nav.pagination a[aria-label=\"Next page\"]") != null

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val firstDocument = client.get(getMangaUrl(manga)).asJsoup()
        val details = parseMangaDetails(firstDocument)

        val chapterList = mutableListOf<SChapter>()
        if (fetchChapters) {
            // Chapters are server-paginated via ?chapterPage=N; follow "Next page" until exhausted.
            var document = firstDocument
            while (true) {
                chapterList += document.select("a.release-row").map(::releaseRowToChapter)
                val nextUrl = document.selectFirst("nav.pagination a[aria-label=\"Next page\"]")?.attr("abs:href")
                    ?: break
                document = client.get(nextUrl).asJsoup()
            }
        }

        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        val facts = document.select("div.series-facts dl > div").associate { row ->
            val label = row.selectFirst("dt")?.text()?.trim()?.lowercase().orEmpty()
            label to row.selectFirst("dd")
        }

        title = document.selectFirst("section.profile-manga h1")!!
            .let { it.ownText().trim().ifEmpty { it.text().trim() } }
        author = facts["author(s)"]?.text()?.trim()
        description = document.selectFirst("p.series-summary")?.text()?.trim()
        genre = facts["genre(s)"]?.select("a")?.joinToString(", ") { it.text().trim() }
            .takeUnless { it.isNullOrEmpty() }
        val statusText = facts["status"]?.text().orEmpty()
        status = when {
            statusText.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
            statusText.contains("completed", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.series-cover-frame img")?.attr("abs:src")
    }

    private fun releaseRowToChapter(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("strong")?.text()?.trim().orEmpty()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.reader-pages figure.reader-page-image img").mapIndexed { index, img ->
            val url = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(index, imageUrl = url)
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = client.get("$baseUrl/genre").asJsoup()
            .select(".genre-directory a")
            .map { GenreOption(it.text().trim(), it.attr("href").removePrefix("/")) }
        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        val options = listOf(
            GenreOption("All", ""),
            GenreOption("Completed", "completed"),
        ) + genres
        return FilterList(
            Filter.Header("Note: text search ignores the genre filter."),
            GenreFilter(options),
        )
    }

    @Serializable
    class GenreOption(val name: String, val id: String)

    private class GenreFilter(private val options: List<GenreOption>) : Filter.Select<String>("Genre", options.map { it.name }.toTypedArray()) {
        val selected: GenreOption get() = options[state]
    }
}
