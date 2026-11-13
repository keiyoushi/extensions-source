package eu.kanade.tachiyomi.extension.en.rule34comic

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Rule34Comic : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getAlbumList(siteUrl(), HOME_BLOCK, "album_viewed", page)

    override suspend fun getLatestUpdates(page: Int) = getAlbumList(siteUrl(), HOME_BLOCK, "post_date", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().value
        if (query.isBlank()) return getAlbumList(siteUrl(), HOME_BLOCK, sort, page)
        return getAlbumList(siteUrl("search"), SEARCH_BLOCK, sort, page, query.trim())
    }

    private fun siteUrl(vararg segments: String): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach(::addPathSegment)
        addPathSegment("")
    }.build()

    // Each list is a block of its page, which the site's pager loads on its own
    private suspend fun getAlbumList(pageUrl: HttpUrl, block: String, sort: String, page: Int, query: String? = null): MangasPage {
        val url = pageUrl.newBuilder()
            .addQueryParameter("mode", "async")
            .addQueryParameter("function", "get_block")
            .addQueryParameter("block_id", block)
            .addQueryParameter("sort_by", sort)
            .apply {
                if (query == null) {
                    addQueryParameter("from", page.toString())
                } else {
                    addQueryParameter("q", query)
                    addQueryParameter("from_albums", page.toString())
                }
            }
            .build()
        val document = client.get(url, Fetch.XHR.from(pageUrl)).asJsoup()
        // Ad cards sit among the albums
        val mangas = document.select("div.item.thumb:not(.adv-card) > a.th").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".thumb_title")!!.text()
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".pagination .item.active ~ .item") != null)
    }

    // The newest albums (/comic/<page>/), and the tag, category, artist and search pages
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val listPage = segments.getOrNull(1)?.toIntOrNull()
        return when {
            url.host.removePrefix("www.") != baseUrl.toHttpUrl().host -> super.getMangasByUrl(url, page)
            segments == listOf("comic") || (segments.size == 2 && segments[0] == "comic" && listPage != null) ->
                getAlbumList(siteUrl(), HOME_BLOCK, "post_date", (listPage ?: 1) + page - 1)
            segments.size == 2 && segments[0] == "search" -> getAlbumList(siteUrl("search"), SEARCH_BLOCK, "post_date", page, segments[1])
            segments.size == 2 && segments[0] in LIST_TYPES -> getAlbumList(siteUrl(segments[0], segments[1]), LIST_BLOCK, "post_date", page)
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || segments.size != 3 || segments[0] != "comic") return null
        val manga = SManga.create().apply { this.url = "/comic/${segments[1]}/${segments[2]}/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val rows = document.select(".album-meta-row").associate { row ->
            row.selectFirst(".album-meta-row__label")!!.text().removeSuffix(":") to row.selectFirst(".album-meta-row__values")!!
        }
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.album-hero__title")!!.text()
            // The site files albums without a known artist under its unknown-artist page
            artist = rows["Artists"]?.select("a:not([href$=/unknown-artist/])")?.joinToString { it.ownText() }?.ifEmpty { null }
            author = artist
            genre = rows["Tags"]?.select("a.tag_item")?.joinToString { it.ownText() }?.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .info(rows["Pages"]?.text()?.let { "$it pages" })
                .field("Categories", rows["Categories"]?.select("a")?.map { it.ownText() }.orEmpty())
                .build()
            thumbnail_url = manga.thumbnail_url ?: document.selectFirst("img.thumb:not(.lazy-load)")?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = rows["Uploaded"]?.selectFirst(".meta_plain")?.text()?.let(::parseRelativeDate) ?: 0L
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    // The album's original images, which the site serves through a redirect
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("a.item[href*=/get_image/]").mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    override fun getFilterList(data: JsonElement?) = getFilters()

    // "4 hours ago", "2 years ago"
    private fun parseRelativeDate(text: String): Long {
        val (amount, unit) = RELATIVE_DATE.find(text)?.destructured ?: return 0L
        val duration = when (unit) {
            "second" -> 1.seconds
            "minute" -> 1.minutes
            "hour" -> 1.hours
            "day" -> 1.days
            "week" -> 7.days
            "month" -> 30.days
            else -> 365.days
        }
        return (Clock.System.now() - duration * amount.toInt()).toEpochMilliseconds()
    }

    companion object {
        private const val HOME_BLOCK = "list_albums_most_recent_albums"
        private const val LIST_BLOCK = "list_albums_common_albums_list"
        private const val SEARCH_BLOCK = "list_albums_albums_list_search_result"
        private val LIST_TYPES = setOf("tags", "categories", "models")
        private val RELATIVE_DATE = Regex("""(\d+) (second|minute|hour|day|week|month|year)s? ago""")
    }
}
