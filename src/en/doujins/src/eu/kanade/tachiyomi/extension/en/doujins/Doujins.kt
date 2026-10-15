package eu.kanade.tachiyomi.extension.en.doujins

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Doujins : KeiSource() {

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val chapterUrl = response.request.url.toString()
        val document = response.asJsoup()

        manga.apply {
            title = document.select(".folder-title a").last()!!.text()
            artist = document.select(".gallery-artist a").joinToString { it.text() }
            author = artist
            genre = document.select(".tag-area").first()!!.select("a").joinToString { it.text() }
        }

        val chapter = SChapter.create().apply {
            name = "Chapter"
            scanlator = document.select("div.folder-message:contains(Translated)").text().substringAfter("by:").trim()
            setUrlWithoutDomain(chapterUrl)

            val dateAndPageCountString = document.select(".text-md-right.text-sm-left > .folder-message").text()

            val date = dateAndPageCountString.substringBefore(" • ")
            for (dateFormat in MANGA_DETAILS_DATE_FORMAT) {
                if (date_upload == 0L) {
                    date_upload = dateFormat.tryParseDate(date)
                } else {
                    break
                }
            }
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get(getLatestPageUrl(page)).parseAs<FoldersDto>().folders.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.link)
                title = it.name
                artist = it.artistList
                author = artist
                genre = it.tags.joinToString(", ") { it.tag }
                thumbnail_url = it.thumbnail2
            }
        }
        return MangasPage(mangas, true)
    }

    private fun getLatestPageUrl(page: Int): String {
        val endDate = LocalDate.now(ZoneOffset.UTC)
            .plusDays(1)
            .minusDays(PAGE_DAYS * (page - 1L))

        val endDateSec = endDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val startDateSec = endDate.minusDays(PAGE_DAYS).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

        return "$baseUrl/folders?start=$startDateSec&end=$endDateSec"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val pageUrl = response.request.url.toString()
        val document = response.asJsoup()
        return document.select(".doujin").mapIndexed { i, page ->
            Page(i, "$pageUrl${page.attr("data-link")}", page.attr("data-file").replace("amp;", ""))
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseGalleryPage(client.get("$baseUrl/top/month").asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val seriesFilter = filters.firstInstance<SeriesFilter>()
        val sortFilter = filters.firstInstance<SortFilter>()
        val popularityPeriodFilter = filters.firstInstance<PopularityPeriodFilter>()

        val url = when {
            query != "" -> {
                "$baseUrl/searches".toHttpUrl().newBuilder()
                    .addQueryParameter("words", query)
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("sort", sortFilter.toUriPart())
                    .build()
            }

            seriesFilter.toUriPart() != "" -> {
                "$baseUrl${seriesFilter.toUriPart()}".toHttpUrl().newBuilder()
                    .addQueryParameter("sort", sortFilter.toUriPart())
                    .build()
            }

            else -> {
                "$baseUrl${popularityPeriodFilter.toUriPart()}".toHttpUrl()
            }
        }

        return parseGalleryPage(client.get(url).asJsoup())
    }

    private fun parseGalleryPage(document: Document): MangasPage {
        val pagination = document.select(".pagination").first()
        return MangasPage(
            document.select("div:not(.premium-folder) > .thumbnail-doujin a.gallery-visited-from-favorites").map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.select("div.title .text").text()
                    artist = it.parent()!!.nextElementSibling()!!.select(".single-line strong").last()
                        ?.text()?.substringAfter("Artist: ")
                    author = artist
                    thumbnail_url = it.select("img").attr("srcset")
                }
            },
            if (pagination != null) {
                !pagination.select("li.page-item:last-child").hasClass("disabled")
            } else {
                false
            },
        )
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Text search ignores series and period filters"),
        Filter.Separator(),

        Filter.Header("Series filter overrides period filter"),
        SeriesFilter(),
        Filter.Separator(),

        Filter.Header("Period filter only applies at initial page"),
        PopularityPeriodFilter(),
        Filter.Separator(),

        Filter.Header("Sort only works with text search and series filter"),
        SortFilter(),
    )

    private class SeriesFilter :
        UriPartFilter(
            "Series",
            arrayOf(
                Pair("None", ""),
                Pair("Doujins - Original Series", "/doujins-original-series-19934"),
                Pair("Hentai Magazine Chapters", "/hentai-magazine-chapters-2766"),
                Pair("Hentai Manga", "/hentai-manga-19"),
                Pair("Fate Grand Order", "/fate-grand-order-doujins-28615"),
                Pair("CG Sets - Original Series", "/cg-sets-original-series-14865"),
                Pair("Touhou", "/touhou-doujins-7748"),
                Pair("Naruto", "/naruto-doujins-5761"),
                Pair("Kantai Collection", "/kantai-collection-doujins-22720"),
                Pair("Hentai Game CG-Sets", "/hentai-game-cg-sets-2422"),
                Pair("One Piece", "/one-piece-doujins-6080"),
                Pair("Granblue Fantasy", "/granblue-fantasy-doujins-28177"),
                Pair("Azur Lane", "/azur-lane-doujins-34298"),
                Pair("Sword Art Online", "/sword-art-online-doujins-7246"),
                Pair("Idolmaster", "/idolmaster-4281"),
                Pair("My Hero Academia", "/my-hero-academia-doujins-28744"),
                Pair("Love Live", "/love-live-doujins-21865"),
                Pair("Pokemon", "/pokemon-doujins-6393"),
                Pair("Dragon Ball", "/dragon-ball-doujins-1238"),
                Pair("CGs - Mixed Series", "/cgs-mixed-series-35311"),
                Pair("Doujins - Mixed Series", "/doujins-mixed-series-20091"),
                Pair("Hentai Magazine Chapters", "/hentai-magazine-chapters-2766"),
                Pair("Hentai Magazine Chapters - Super-Shorts", "/hentai-magazine-chapters-super-shorts-19933"),
                Pair("Hentai Manga", "/hentai-manga-19"),
            ),
        )

    private class SortFilter :
        UriPartFilter(
            "Sort",
            arrayOf(
                Pair("Newest First", ""),
                Pair("Oldest First", "created_at"),
                Pair("Alphabetical", "name"),
                Pair("Rating", "-cached_score"),
                Pair("Popularity", "-cached_views"),
            ),
        )

    private class PopularityPeriodFilter :
        UriPartFilter(
            "Period",
            arrayOf(
                Pair("This Month", "/top"),
                Pair("This Year", "/top/year"),
                Pair("All Time", "/top/all"),
            ),
        )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        private const val PAGE_DAYS = 3L
        private val ORDINAL_SUFFIXES = listOf("th", "st", "nd", "rd")
        private val MANGA_DETAILS_DATE_FORMAT = ORDINAL_SUFFIXES.map {
            DateTimeFormatter.ofPattern("MMMM d'$it', yyyy", Locale.US)
        }
    }
}
