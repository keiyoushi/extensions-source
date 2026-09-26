package eu.kanade.tachiyomi.multisrc.mangadventure

import android.os.Build.VERSION
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

/** MangAdventure base source. */
abstract class MangAdventure : KeiSource() {
    /** The site's manga categories. */
    protected open val categories = DEFAULT_CATEGORIES

    /** The site's manga status names. */
    protected open val statuses = arrayOf("Any", "Completed", "Ongoing", "Hiatus", "Cancelled")

    /** The site's sort order labels that correspond to [SortOrder.values]. */
    protected open val orders = arrayOf(
        "Title",
        "Views",
        "Latest upload",
        "Chapter count",
    )

    /** A user agent representing Tachiyomi. */
    private val userAgent =
        "Mozilla/5.0 (Android ${VERSION.RELEASE}; Mobile) Tachiyomi/${AppInfo.getVersionName()}"

    /** The URL of the site's API. */
    private val apiUrl by lazy { "$baseUrl/api/v2" }

    override val supportsLatest = true

    override fun Headers.Builder.configureHeaders() = set("User-Agent", userAgent)

    override suspend fun getLatestUpdates(page: Int) = parseMangasPage(client.get("$apiUrl/series?page=$page&sort=-latest_upload"))

    override suspend fun getPopularManga(page: Int) = parseMangasPage(client.get("$apiUrl/series?page=$page&sort=-views"))

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) return null
        return fetchManga(url.pathSegments[1])
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder().addEncodedPathSegment("series").apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("title", query)
            filters.filterIsInstance<UriFilter>().forEach {
                addQueryParameter(it.param, it.toString())
            }
        }.build()

        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response) = response.parseAs<Paginator<Series>>().let {
        MangasPage(it.map(::mangaFromJSON), !it.last)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchManga(manga.url) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchManga(slug: String) = mangaFromJSON(client.get("$apiUrl/series/$slug").parseAs<Series>())

    private suspend fun fetchChapterList(manga: SManga) = client.get("$apiUrl/series/${manga.url}/chapters?date_format=timestamp")
        .parseAs<Results<Chapter>>().map { chapter ->
            SChapter.create().apply {
                url = chapter.id.toString()
                name = buildString {
                    append(chapter.fullTitle)
                    if (chapter.final) append(" [END]")
                }
                chapter_number = chapter.number
                date_upload = chapter.published.toLong()
                scanlator = chapter.groups.joinToString()
            }
        }

    override suspend fun getPageList(chapter: SChapter) = client.get("$apiUrl/chapters/${chapter.url}/pages?track=true")
        .parseAs<Results<MAPage>>().map { page ->
            Page(page.number, imageUrl = page.image)
        }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/reader/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$apiUrl/chapters/${chapter.url}/read"

    override fun getFilterList(data: JsonElement?) = FilterList(
        Author(),
        Artist(),
        Status(statuses),
        SortOrder(orders),
        CategoryList(categories),
    )

    /** Converts a [Series] object to an [SManga]. */
    private fun mangaFromJSON(series: Series) = SManga.create().apply {
        url = series.slug
        title = series.title
        thumbnail_url = series.cover
        description = buildString {
            series.description?.let(::append)
            series.aliases.let {
                if (!it.isNullOrEmpty()) {
                    it.joinTo(this, "\n", "\n\nAlternative titles:\n")
                }
            }
        }
        author = series.authors?.joinToString()
        artist = series.artists?.joinToString()
        genre = series.categories?.joinToString()
        status = if (series.licensed == true) {
            SManga.LICENSED
        } else {
            when (series.status) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                "hiatus" -> SManga.ON_HIATUS
                "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    companion object {
        /** Manga categories from MangAdventure `categories.xml` fixture. */
        val DEFAULT_CATEGORIES = listOf(
            "4-Koma",
            "Action",
            "Adventure",
            "Comedy",
            "Doujinshi",
            "Drama",
            "Ecchi",
            "Fantasy",
            "Gender Bender",
            "Harem",
            "Hentai",
            "Historical",
            "Horror",
            "Josei",
            "Martial Arts",
            "Mecha",
            "Mystery",
            "Psychological",
            "Romance",
            "School Life",
            "Sci-Fi",
            "Seinen",
            "Shoujo",
            "Shoujo Ai",
            "Shounen",
            "Shounen Ai",
            "Slice of Life",
            "Smut",
            "Sports",
            "Supernatural",
            "Tragedy",
            "Yaoi",
            "Yuri",
        )
    }
}
