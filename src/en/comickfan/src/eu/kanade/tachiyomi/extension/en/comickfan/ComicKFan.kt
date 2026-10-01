package eu.kanade.tachiyomi.extension.en.comickfan

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class ComicKFan : KeiSource() {
    // Popular
    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.POPULAR)

    // Latest
    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        // Rewrite to strip suffixes after slug
        val newUrl = "$baseUrl/manga/$slug"
        return fetchMangaUpdate(
            SManga.create().apply {
                setUrlWithoutDomain(newUrl)
            },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var genres = ""
        var status = ""
        var type = ""
        var sort = ""

        filters.forEach { filter ->
            when (filter) {
                is FormatGenreFilter -> genres += filter.selected
                is ContentGenreFilter -> genres += filter.selected
                is ThemeGenreFilter -> genres += filter.selected
                is GenreGenreFilter -> genres += filter.selected
                is StatusFilter -> status = filter.toUriPart()
                is TypeFilter -> type = filter.toUriPart()
                is SortFilter -> sort = filter.toUriPart()
                else -> {}
            }
        }
        val url = "$baseUrl/advanced-search".toHttpUrl().newBuilder()
            .addQueryParameter("genres", genres)
            .addQueryParameter("status", status)
            .addQueryParameter("type", type)
            .addQueryParameter("sort", sort)
            .addQueryParameter("name", query)
            .addQueryParameter("page", page.toString())
            .build()

        return parseSearch(client.get(url))
    }

    private fun parseSearch(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("div:has(> form) + div.grid > a")
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst("a:has(img[alt=Next])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))

        val img = element.selectFirst("img")!!
        title = img.attr("alt")
        thumbnail_url = img.absUrl("src")
    }

    // Details + Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        val detailsDeferred = async {
            if (fetchDetails) {
                val doc = client.get(getMangaUrl(manga)).asJsoup()
                parseDetails(doc)
            } else {
                manga
            }
        }
        val chaptersDeferred = async { if (fetchChapters) getChapters(manga) else chapters }
        SMangaUpdate(
            detailsDeferred.await(),
            chaptersDeferred.await(),
        )
    }

    private fun parseDetails(document: Document): SManga {
        val infoRoot = document.selectFirst("div[class=bg-card-section]")

        return SManga.create().apply {
            setUrlWithoutDomain(document.location())
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("div.comic-content.desk")?.text()
            author = infoRoot?.getValue("Author")?.split(",")?.joinToString()
            artist = infoRoot?.getValue("Artist")?.split(",")?.joinToString()
            genre = infoRoot?.select("div.font-medium:contains(Genres) + div a")?.joinToString(transform = Element::text)

            status = when (infoRoot?.getValue("Status")?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                // "cancelled" -> SManga.CANCELLED // Shows as '❓ Unknown'
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = infoRoot?.selectFirst("div.thumb-cover img")?.absUrl("src")
        }
    }

    private fun Element.getValue(label: String): String? = select("div.flex-row.gap-4")
        .firstOrNull { it.selectFirst("> div.text-sm")?.text()?.equals(label) == true }
        ?.selectFirst("> div.text-sm:nth-child(2):last-child")
        ?.takeIf { it.text() !in listOf("", "-", "_") }
        ?.text()

    private suspend fun getChapters(manga: SManga): List<SChapter> {
        val comicId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.getOrNull(1)
            ?: throw Exception("Invalid manga URL: ${manga.url}")

        val response = client.get("$baseUrl/api/comics/$comicId/chapter-list?translation_group_id=")

        return response.parseAs<ComicKFanChapterListResponseDto>().data
            .map { it.toSChapter(comicId) }
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = document.select("div.w-full > img[loading=lazy]")
        return pages.mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        FormatGenreFilter(),
        ContentGenreFilter(),
        ThemeGenreFilter(),
        GenreGenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )

    private fun ComicKFanChapterDto.toSChapter(comicId: String) = SChapter.create().apply {
        setUrlWithoutDomain("/manga/$comicId/chapter-$chapter-$hashId")
        name = "Chapter $chapter"
        scanlator = groupNames.joinToString()
        chapter.toFloatOrNull()?.also { chapter_number = it }
        date_upload = Instant.tryParse(createdAt ?: publishedAt)
    }
}
