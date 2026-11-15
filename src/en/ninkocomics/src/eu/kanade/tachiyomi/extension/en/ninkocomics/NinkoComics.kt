package eu.kanade.tachiyomi.extension.en.ninkocomics

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NinkoComics : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getComics("popular", "", "")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getComics("latest", "", "")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return getComics(
                filters.firstInstance<SortFilter>().value,
                filters.firstInstance<GenreFilter>().value,
                filters.firstInstance<StatusFilter>().value,
            )
        }
        val url = siteUrl("search").addQueryParameter("q", query.trim()).build()
        return MangasPage(client.get(url, Fetch.DOCUMENT).asJsoup().comics(), false)
    }

    // The whole catalogue is on one page
    private suspend fun getComics(sort: String, genre: String, status: String): MangasPage {
        val url = siteUrl("comics").apply {
            addQueryParameter("sort", sort)
            if (genre.isNotEmpty()) addQueryParameter("genre", genre)
            if (status.isNotEmpty()) addQueryParameter("status", status)
        }.build()
        return MangasPage(client.get(url, Fetch.DOCUMENT).asJsoup().comics(), false)
    }

    private fun Document.comics(): List<SManga> = select("a.rk-poster[href^=/comic/]").map { poster ->
        SManga.create().apply {
            setUrlWithoutDomain(poster.absUrl("href"))
            title = poster.selectFirst("span.line-clamp-2")!!.text()
            thumbnail_url = poster.selectFirst("img")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val path = when (url.pathSegments[0]) {
            "comic" -> "/comic/$slug/"
            // A chapter page links back to its comic
            "chapter" -> client.get(siteUrl("chapter", slug).build(), Fetch.DOCUMENT).asJsoup()
                .selectFirst("a[href^=/comic/]")!!.absUrl("href").toHttpUrl().encodedPath
            else -> return null
        }
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val series = document.selectFirst("script[type=application/ld+json]:containsData(ComicSeries)")!!.data().parseAs<SeriesDto>()
        // Badges above the title: type, status and release day
        val badges = document.selectFirst("h1")!!.previousElementSibling()?.children()?.map { it.text() }.orEmpty()
        val status = when {
            "Ongoing" in badges -> SManga.ONGOING
            "Completed" in badges -> SManga.COMPLETED
            "Hiatus" in badges -> SManga.ON_HIATUS
            "Dropped" in badges -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val details = series.toSManga(
            manga.url,
            document.selectFirst("[data-clamp]"),
            badges.firstOrNull { it.startsWith("New chapter") },
            status,
        )

        val chapterList = document.select("li[data-chapter-id] > a").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                val title = link.selectFirst("span.min-w-0 > span")!!.text()
                // Early access chapters cost coins until their free day
                name = if (link.hasAttr("data-unlock")) "🔒 $title" else title
                date_upload = DATE_FORMAT.tryParseDate(link.selectFirst("[data-percent-label]")?.attr("title"))
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("img[src*=/media/pages/]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // Pages end with a slash, as the site links them
    private fun siteUrl(vararg segments: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach { addPathSegment(it) }
        addPathSegment("")
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    }
}
