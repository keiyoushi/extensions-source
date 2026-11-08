package eu.kanade.tachiyomi.extension.en.mangahome

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaHome : KeiSource() {
    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList("$baseUrl/directory/$page.htm".toHttpUrl())

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList("$baseUrl/latest/$page.html".toHttpUrl())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searching = query.isNotBlank() || filters.any { filter ->
            when (filter) {
                is TypeFilter -> filter.state != 0
                is RatingFilter -> filter.state != 0
                is CompletedFilter -> filter.state != 0
                is Filter.Text -> filter.state.isNotBlank()
                is GenreFilter -> filter.state.any { !it.isIgnored() }
                else -> false
            }
        }
        if (!searching) {
            val list = filters.firstInstanceOrNull<ListFilter>()?.selected ?: "directory"
            val url = "$baseUrl/$list/$page.htm".toHttpUrl().newBuilder()
            filters.firstInstanceOrNull<SortFilter>()?.selected?.takeIf { it.isNotEmpty() }?.let(url::encodedQuery)
            return mangaList(url.build())
        }

        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("name_method", "cw")
            .addQueryParameter("name", query)
        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> url.addQueryParameter("type", filter.selected)
                is AuthorFilter -> url.addQueryParameter("author_method", "cw").addQueryParameter("author", filter.state)
                is ArtistFilter -> url.addQueryParameter("artist_method", "cw").addQueryParameter("artist", filter.state)
                is GenreFilter ->
                    url
                        .addQueryParameter("ingenres", filter.state.filter { it.isIncluded() }.joinToString(",") { it.value })
                        .addQueryParameter("exgenres", filter.state.filter { it.isExcluded() }.joinToString(",") { it.value })
                is YearMethodFilter -> url.addQueryParameter("released_method", filter.selected)
                is YearFilter -> url.addQueryParameter("released", filter.state)
                is RatingMethodFilter -> url.addQueryParameter("rating_method", filter.selected)
                is RatingFilter -> url.addQueryParameter("rating", filter.selected)
                is CompletedFilter -> url.addQueryParameter("is_completed", filter.selected)
                else -> {}
            }
        }
        url.addQueryParameter("advopts", "1")
        return mangaList(url.build())
    }

    private suspend fun mangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("ul.manga-list .post").map { element ->
            val link = element.selectFirst("p.title a")!!
            SManga.create().apply {
                this.url = link.absUrl("href").toHttpUrl().pathSegments[1]
                // Link text is cut with "..." on long titles; the title attribute is cut at unescaped quotes
                title = link.text().takeUnless { it.endsWith("...") } ?: link.attrOrNull("title")!!
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".page-nav a.next") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.").removePrefix("m.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetails(manga, document), chapterList(document))
    }

    private fun mangaDetails(manga: SManga, document: Document) = manga.apply {
        val detail = document.selectFirst(".manga-detail") ?: throw Exception("Series not found")
        val info = detail.selectFirst(".detail-info")!!
        val middle = detail.selectFirst(".manga-detailmiddle")!!
        title = detail.selectFirst("h1")!!.text()
        thumbnail_url = detail.selectFirst("img.detail-cover")?.absUrl("src")
        author = info.select("p:has(span:containsOwn(Author)) a").joinToString { it.text() }.ifEmpty { null }
        artist = info.select("p:has(span:containsOwn(Artist)) a").joinToString { it.text() }.ifEmpty { null }
        genre = middle.select("p:has(span:containsOwn(Genre)) a").joinToString { it.text() }.ifEmpty { null }
        status = if (document.selectFirst(LICENSED_SELECTOR) != null) {
            SManga.LICENSED
        } else {
            when (info.selectFirst("p:has(span:containsOwn(Status))")?.ownText()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        // "(Need 5 Votes)" until a series has enough votes for a rating
        val votes = info.selectFirst(".count-number")?.text()?.let { votesRegex.find(it) }?.groupValues?.get(1)?.toInt()
        val rank = info.selectFirst("p:has(span:containsOwn(Rank))")?.ownText()?.toIntOrNull()
        description = MarkdownDescription.Builder()
            .rating(info.selectFirst(".score-number")?.text()?.toDoubleOrNull()?.takeIf { votes != null }, max = 5, votes = votes)
            .info(rank?.let { "Rank #$it" })
            .description(middle.selectFirst("p.hide.mobile-none")?.wholeOwnText()?.lines()?.joinToString("\n") { it.trim() })
            .altTitles(middle.selectFirst("p:has(> span:containsOwn(Alternative Name))")?.ownText()?.split(";").orEmpty())
            .build()
    }

    private fun chapterList(document: Document): List<SChapter> = document.select("ul.detail-chlist > li").map { element ->
        val link = element.selectFirst("a")!!
        val path = link.absUrl("href").toHttpUrl().encodedPathSegments.drop(1).filter { it.isNotEmpty() }
        SChapter.create().apply {
            url = path.joinToString("/")
            // c000 rows are named without a number
            chapterNumberRegex.matchEntire(path.last())?.let { chapter_number = it.groupValues[1].toFloat() }
            val number = link.selectFirst("span.pc-none")!!.text().takeIf { it != "Ch" }
            name = listOfNotNull(number, element.selectFirst("span.vol")?.ownTextOrNull()).joinToString(" - ").ifEmpty { "Ch 0" }
            date_upload = dateFormat.tryParseDate(element.selectFirst("span.time")?.text()?.substringAfter("day "))
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(LICENSED_SELECTOR) != null) return emptyList()
        val script = document.selectFirst("script:containsData(var chapter_id)")!!.data()
        val chapterId = chapterIdRegex.find(script)!!.groupValues[1]
        val imageCount = imageCountRegex.find(script)!!.groupValues[1].toInt()
        return List(imageCount) { index ->
            val url = chapterUrl.newBuilder()
                .addPathSegment("chapterfun.ashx")
                .addQueryParameter("cid", chapterId)
                .addQueryParameter("page", (index + 1).toString())
                .addQueryParameter("key", "")
                .build()
            Page(index, url = url.toString())
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val url = page.url.toHttpUrl()
        val chapterUrl = url.newBuilder().removePathSegment(url.pathSize - 1).query(null).build()
        val script = Unpacker.unpack(client.get(url, Fetch.XHR.from(chapterUrl)).body.string())
        val host = imageHostRegex.find(script)?.groupValues?.get(1) ?: throw Exception("Image host not found")
        val path = imagePathRegex.find(script)?.groupValues?.get(1) ?: throw Exception("Image path not found")
        return "https:$host$path"
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/directory", Fetch.DOCUMENT).asJsoup()
        .select(".filter-list li:has(label:containsOwn(Genres)) a[title]")
        .map { Genre(it.attr("title"), it.absUrl("href").toHttpUrl().pathSegments[0]) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>().orEmpty()
        return FilterList(
            buildList {
                add(Filter.Header("Without search text or the filters below, browse by list and sort"))
                add(ListFilter())
                add(SortFilter())
                add(Filter.Separator())
                add(TypeFilter())
                add(AuthorFilter())
                add(ArtistFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
                add(YearMethodFilter())
                add(YearFilter())
                add(RatingMethodFilter())
                add(RatingFilter())
                add(CompletedFilter())
            },
        )
    }

    companion object {
        private const val LICENSED_SELECTOR = "div:containsOwn(has been licensed)"
        private val dateFormat = DateTimeFormatter.ofPattern("MMM dd,yyyy", Locale.ENGLISH)
        private val chapterIdRegex = Regex("""var chapter_id\s*=\s*(\d+)""")
        private val imageCountRegex = Regex("""var imagecount\s*=\s*(\d+)""")
        private val votesRegex = Regex("""^\((\d+) Votes\)$""")
        private val chapterNumberRegex = Regex("""c(\d+(?:\.\d+)?)""")
        private val imageHostRegex = Regex("""var pix="([^"]+)"""")
        private val imagePathRegex = Regex("""var pvalue=\["([^"]+)"""")
    }
}
