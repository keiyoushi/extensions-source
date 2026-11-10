package eu.kanade.tachiyomi.extension.en.manhwa210

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class Manhwa210 : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(searchUrl("-views", page), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(searchUrl("-updated_at", page), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "-views"
        val url = searchUrl(sort, page).newBuilder().apply {
            if (query.isNotBlank()) {
                val type = filters.firstInstanceOrNull<SearchTypeFilter>()?.value ?: "name"
                addQueryParameter("filter[$type]", query.trim())
            }
            val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            genres.filter { it.isIncluded() }.takeIf { it.isNotEmpty() }?.let { included ->
                addQueryParameter("filter[accept_genres]", included.joinToString(",") { it.id })
            }
            genres.filter { it.isExcluded() }.takeIf { it.isNotEmpty() }?.let { excluded ->
                addQueryParameter("filter[reject_genres]", excluded.joinToString(",") { it.id })
            }
        }.build()
        return getMangaList(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("span.grow.text-lg")!!.text()
            thumbnail_url = document.selectFirst(".cover-frame .cover")?.backgroundImage()
            author = document.infoRow("Artist")?.select("a")?.eachText()?.joinToString()?.ifEmpty { null }
            artist = author
            genre = document.infoRow("Genres")?.select("a")?.eachText()?.joinToString()?.ifEmpty { null }
            status = when (document.infoRow("Status")?.selectFirst("a")?.text()?.lowercase()) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            val altTitles = document.infoRow("Alt name")?.selectFirst("span:not(.text-gray-500)")?.text()
                ?.split(" / ")
                ?.filterNot { it == "N/A" }
                .orEmpty()
            description = MarkdownDescription.Builder()
                .description(document.select("div.py-4:has(> p.text-lg:containsOwn(Plot)) > p:not(.text-lg)").eachText().joinToString("\n\n"))
                .altTitles(altTitles)
                .build()
        }
        val chapterList = document.select("ul.overflow-y-auto > a[href*='/manga/']").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("span.text-ellipsis")!!.text()
                date_upload = DATE_FORMAT.tryParseDateTime(element.selectFirst("span.timeago")?.attrOrNull("datetime"))
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("div.text-center > img.lazy")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/search", Fetch.DOCUMENT).asJsoup()
        .select("label")
        .mapNotNull { label ->
            val id = GENRE_ID_REGEX.find(label.attr("@click"))?.groupValues?.get(1) ?: return@mapNotNull null
            GenreOption(label.text(), id)
        }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                SearchTypeFilter(),
                GenreFilter(genres).takeIf { genres.isNotEmpty() },
            ),
        )
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .selectFirst("h5:has(> span:containsOwn(You may like))")
        ?.parent()
        ?.select("div.flex.gap-2")
        ?.mapNotNull { card ->
            val link = card.selectFirst("a[href*='/manga/']") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst(".cover-sm")?.backgroundImage()
            }
        }
        .orEmpty()

    private fun searchUrl(sort: String, page: Int): HttpUrl = "$baseUrl/search".toHttpUrl().newBuilder()
        .addQueryParameter("sort", sort)
        .addQueryParameter("page", page.toString())
        .build()

    private suspend fun getMangaList(url: HttpUrl, page: Int): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.manga-vertical").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("div.p-2 a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst(".cover")?.backgroundImage()
            }
        }
        // The pager's "next" button always leads to the last page
        val hasNextPage = document.selectFirst("a.page-link[href*='page=${page + 1}']") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Document.infoRow(label: String): Element? = selectFirst("div.mt-2:has(> span.text-gray-500:containsOwn($label))")

    private fun Element.backgroundImage(): String? = BACKGROUND_IMAGE_REGEX.find(attr("style"))?.groupValues?.get(1)

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val BACKGROUND_IMAGE_REGEX = Regex("""url\(['"]?(.*?)['"]?\)""")
        private val GENRE_ID_REGEX = Regex("""toggleGenre\('(\d+)'\)""")
    }
}
