package eu.kanade.tachiyomi.extension.en.keroscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.collections.map
import kotlin.time.Instant

@Source
abstract class Keroscans :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = getApiMangasPage(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getApiMangasPage(page, "latest")

    private suspend fun getApiMangasPage(page: Int, sort: String): MangasPage = getMangasPage(
        "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .build(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangasPage(
        baseUrl.toHttpUrl().newBuilder()
            .apply {
                if (query.length < 3) {
                    addPathSegment("series")
                } else {
                    addPathSegment("search")
                    addQueryParameter("q", query)
                }
                filters.filterIsInstance<UriQueryFilter>().forEach { it.addToQuery(this) }
                addQueryParameter("page", page.toString())
            }
            .build(),
    )

    private suspend fun getMangasPage(pageUrl: HttpUrl): MangasPage {
        val document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        return MangasPage(
            document.select("article").map {
                val a = it.selectFirst("a")!!
                val cover = a.selectFirst(".ks-cover")!!
                SManga.create().apply {
                    title = a.selectFirst("h3")!!.text().trim()
                    url = a.attr("href")
                    thumbnail_url = cover.selectFirst("img")?.absUrl("src")
                    memo = buildJsonObject {
                        put("id", url)
                        put("slug", url.substringAfterLast('/'))
                    }
                    status = cover.selectFirst("span.top-1\\.5.right-1\\.5")!!.text().trim().toSMangaStatus()
                }
            },
            document.selectFirst("nav[aria-label=\"Pagination\"] a[rel=\"next\"]") != null,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = getMangaSlug(url)?.let { getManga(getMangaDocument(it), it) }

    private fun getMangaSlug(url: HttpUrl): String? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.getOrNull(0) != "series" || segments.getOrNull(1) != "comic") return null
        if (segments.getOrNull(3) == "chapter") return null
        return segments.getOrNull(2)?.takeIf { it.isNotEmpty() }
    }

    private suspend fun getMangaDocument(slug: String): Document = client.get("$baseUrl/series/comic/$slug", Fetch.DOCUMENT).asJsoup()

    private fun getManga(document: Document, slug: String): SManga = SManga.create().apply {
        val detailsElement = document.selectFirst("aside[aria-label=\"Series details\"]")
        val details = detailsElement?.select("dl div:has(dt):has(dd)")?.associate {
            val dt = it.selectFirst("dt")!!.text().trim()
            val dd = it.selectFirst("dd")!!.text().trim()
            dt to dd
        }.orEmpty()

        title = document.selectFirst("#series-title")!!.text().trim()
        thumbnail_url = document.selectFirst("section[aria-label=\"Cover\"] img")?.absUrl("src")
        url = "/series/comic/$slug"
        memo = buildJsonObject {
            put("id", url)
            put("slug", slug)
        }
        author = details["Author"]
        artist = details["Artist"]
        status = details["Status"].toSMangaStatus()
        genre = buildList {
            details["Type"]?.let(::add)
            details["Language"]?.let(::add)
            details["Demographic"]?.let(::add)
            document.select("ul[aria-label=\"Genres\"] li").forEach { add(it.text().trim()) }
            document.select("ul[aria-label=\"Tags\"] li").forEach { add(it.text().substringAfter('#').trim()) }
        }.distinct().joinToString()
        description = MarkdownDescription.Builder().apply {
            info(
                // Currently, no manga has ratings, so the format is unknown. Use .rating() when format is known.
                details["Rating"]?.takeIf { it != "Not rated yet" },
                details["Released"],
                details["Type"],
                details["Format"],
                details["Language"],
            )
            description(document.selectFirst(".ks-sp__summary")?.wholeText()?.trim())
            altTitles(
                detailsElement
                    ?.select("h2:contains(Also known as) + ul li")
                    ?.map { it.text().trim() }
                    ?.filter { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }
                    ?.distinct()
                    ?: emptyList(),
            )
            details["Chapters"]?.let { field("Chapters", it) }
            details["Views"]?.let { field("Views", it) }
        }.build()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga).toHttpUrl()
        val slug = manga.memo["slug"]!!.string
        val mangaDocument = if (fetchDetails) getMangaDocument(slug) else null
        return SMangaUpdate(
            mangaDocument?.let { getManga(mangaDocument, slug) } ?: manga,
            if (fetchChapters) fetchAllChapters(url, slug, mangaDocument) else chapters,
        )
    }

    override val supportsRelatedMangas: Boolean get() = false

    private suspend fun fetchAllChapters(mangaUrl: HttpUrl, mangaSlug: String, mangaDocument: Document? = null): List<SChapter> = buildList {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF, true)
        val query = if (hideLocked) "#chapter-list ol li[data-access=\"free\"]" else "#chapter-list ol li"
        fun Element.toSChapter(): SChapter = SChapter.create().apply {
            val a = selectFirst("a")!!
            val numberString = a.attr("data-chapter-number")
            name = "Chapter $numberString"
            val isLocked = attr("data-access") == "paid"
            if (isLocked) {
                name = "\uD83D\uDD12 $name"
            }
            date_upload = Instant.tryParse(selectFirst("time")?.attr("datetime"))
            chapter_number = numberString.toFloat()
            url = a.attr("href")
            memo = buildJsonObject {
                put("id", url)
                put("slug", mangaSlug)
                put("number", numberString)
                put("isLocked", isLocked)
            }
        }
        val firstPage = if (mangaDocument == null) {
            1
        } else {
            mangaDocument.select(query).forEach { add(it.toSChapter()) }
            if (mangaDocument.selectFirst("#chapter-list div[data-chapter-more-wrap]") == null) return@buildList
            2
        }
        for (page in firstPage..Int.MAX_VALUE) {
            val pageUrl = mangaUrl.newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("partial", "chapters")
                .build()
            val fragment = Jsoup.parseBodyFragment(client.get(pageUrl, Fetch.XHR).body.string(), mangaUrl.toString())
            fragment.select(query).forEach { add(it.toSChapter()) }
            if (fragment.selectFirst("#chapter-list div[data-chapter-more-wrap]") == null) return@buildList
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get(getChapterUrl(chapter), Fetch.DOCUMENT)
        .asJsoup()
        .select("ol.ks-pages li.ks-page img")
        .mapIndexed { index, elements ->
            Page(index, imageUrl = elements.absUrl("src"))
        }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val filtersElement = client
            .get("$baseUrl/series", Fetch.DOCUMENT)
            .asJsoup()
            .selectFirst("dialog[aria-labelledby=\"filters-title\"]")!!
        val genres = filtersElement.select("label:has(input[name=\"genre[]\"])").associate {
            it.text().trim() to it.selectFirst("input")!!.attr("value")
        }
        val tags = filtersElement.select("label:has(input[name=\"tag[]\"])").associate {
            it.text().trim() to it.selectFirst("input")!!.attr("value")
        }
        val years = filtersElement.select("select[name=\"year\"] option").associate {
            it.text().trim() to it.attr("value")
        }
        return FilterData(genres, tags, years, FilterData.CURRENT_VERSION).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()?.apply {
            if (outdated) throw Exception("Filter data outdated")
        }

        return FilterList(
            listOfNotNull(
                filterData?.genres?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
                filterData?.tags?.takeIf { it.isNotEmpty() }?.let(::TagFilter),
                StatusFilter(),
                DemographicFilter(),
                filterData?.years?.takeIf { it.isNotEmpty() }?.let(::YearFilter),
                FormatFilter(),
                RatingFilter(),
                SortFilter(),
            ),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF
            title = "Hide locked chapters"
            summary = "Hide chapters that require coins to read"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]!!.string
        return "$baseUrl/series/comic/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        val number = chapter.memo["number"]!!.string
        return "$baseUrl/series/comic/$slug/chapter/$number"
    }

    private fun String?.toSMangaStatus(): Int = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        "Hiatus" -> SManga.ON_HIATUS
        "Cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val HIDE_LOCKED_PREF = "pref_hide_locked_chapters"
    }
}
