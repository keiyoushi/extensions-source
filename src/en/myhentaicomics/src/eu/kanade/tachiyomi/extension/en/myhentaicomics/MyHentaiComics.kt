package eu.kanade.tachiyomi.extension.en.myhentaicomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MyHentaiComics : KeiSource() {

    // =============================== Popular ================================

    // Popular = most viewed
    override suspend fun getPopularManga(page: Int): MangasPage = parseComicListing("$baseUrl/views/$page".toHttpUrl())

    // =============================== Latest =================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseComicListing("$baseUrl/gallery/$page".toHttpUrl())

    // =============================== Search =================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val groupFilter = filters.firstInstanceOrNull<GroupFilter>()

        // /search accepts the query, tags and sorting together, unlike the listing paths
        val url = "$baseUrl/search/$page".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("query", query)
            }
            categoryFilter?.toUriPart()?.takeIf(String::isNotEmpty)?.let {
                addEncodedQueryParameter("tags[]", it)
            }
            groupFilter?.toUriPart()?.takeIf(String::isNotEmpty)?.let {
                addEncodedQueryParameter("tags[]", it)
            }
            sortFilter?.toUriPart()?.takeIf(String::isNotEmpty)?.let {
                addQueryParameter("sorting", it)
            }
        }.build()

        return parseComicListing(url)
    }

    // ============================== Filters =================================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/tag/group", Fetch.DOCUMENT).asJsoup()

        val groups = document.select("#myList a.listNavItem").map {
            GroupOption(
                name = it.text().replace(GROUP_COUNT_REGEX, "").trim(),
                value = it.attr("href").substringAfterLast('/'),
            )
        }

        return FilterData(groups).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            add(Filter.Separator())
            add(CategoryFilter())

            val groups = data?.parseAs<FilterData>()?.groups.orEmpty()
            if (groups.isNotEmpty()) {
                add(Filter.Separator())
                add(GroupFilter(groups.map { it.name to it.value }))
            }
        },
    )

    // =========================== Comic Listing ==============================

    private suspend fun parseComicListing(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()

        val mangas = document.select("li.item:not(.image-block) .comic-inner a").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.absUrl("href"))
                title = el.select("h2.comic-name").text()
                thumbnail_url = el.select("img").first()?.absUrl("src")?.encodeSpaces()
            }
        }

        val hasNextPage = document.selectFirst("li.next a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val descriptionDiv = document.selectFirst("div.comic-description")

        val categories = descriptionDiv
            ?.select("a[href*='/gallery/category/']")
            ?.map { it.text() }
            .orEmpty()

        val artists = descriptionDiv
            ?.select("a[href*='/gallery/artist/']")
            ?.map { it.text() }
            .orEmpty()

        val groups = descriptionDiv
            ?.select("a[href*='/gallery/group/']")
            ?.map { it.text() }
            .orEmpty()

        val pagesText = descriptionDiv
            ?.select("div")
            ?.firstOrNull { it.ownText().startsWith("Pages:") }
            ?.text()

        val author = artists.joinToString(", ").ifEmpty { null }

        val updatedManga = manga.apply {
            title = descriptionDiv?.selectFirst("h1")?.text() ?: title
            thumbnail_url = document.selectFirst("div.comic-cover img")?.absUrl("src")?.encodeSpaces()
            this.author = author
            this.artist = author
            genre = (categories + groups).joinToString(", ")
            status = SManga.COMPLETED
            description = pagesText.orEmpty()
        }

        // Extract comic ID from the "Back to gallery" / first page link on the thumbnail page
        val firstPageHref = document.selectFirst("div.comic-cover a")?.absUrl("href")
            ?: return SMangaUpdate(updatedManga, emptyList())

        // href = "https://myhentaicomics.com/gallery/show/59109/1"
        val comicId = firstPageHref
            .substringAfter("/gallery/show/")
            .substringBefore("/")

        val updatedChapters = listOf(
            SChapter.create().apply {
                url = "/gallery/show/$comicId/1"
                name = "Chapter 1"
                chapter_number = 1f
                date_upload = 0L
            },
        )

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // ============================== Page List ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()

        // The comic ID from the request URL: /gallery/show/59109/1
        val comicId = document.location()
            .substringAfter("/gallery/show/")
            .substringBefore("/")

        // Get the current page image to derive folder and extension
        val imageUrl = document.selectFirst("ul.gallery-slide li img")?.absUrl("src")
            ?: return emptyList()

        // imageUrl = "https://cdn.myhentaicomics.com/mhc/images/The Mayor 6/original/001.jpg?22"
        val imageBase = imageUrl.substringBeforeLast("/") + "/"
        val fileName = imageUrl.substringAfterLast("/") // "001.jpg?22"
        val fileExtension = fileName.substringAfter(".") // "jpg?22"

        // Find total page count from all pagination links pointing to this comic
        val totalPages = document
            .select("ul li a[href*='/gallery/show/$comicId/']")
            .mapNotNull { it.attr("href").substringAfterLast("/").toIntOrNull() }
            .maxOrNull() ?: 1

        return (1..totalPages).mapIndexed { index, pageNum ->
            val paddedNum = pageNum.toString().padStart(3, '0')
            Page(index, imageUrl = "${imageBase}$paddedNum.$fileExtension".encodeSpaces())
        }
    }

    // ============================== Helpers =================================

    private fun String.encodeSpaces(): String = replace(" ", "%20")

    companion object {
        private val GROUP_COUNT_REGEX = Regex("""\s*\(\d+\)$""")
    }
}
