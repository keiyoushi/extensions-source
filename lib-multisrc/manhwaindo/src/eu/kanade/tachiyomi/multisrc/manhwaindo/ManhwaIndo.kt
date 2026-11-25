package eu.kanade.tachiyomi.multisrc.manhwaindo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZonedDateTime

abstract class ManhwaIndo : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter(1)))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter(0)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            if (page > 1) addPathSegments("page/$page")
            addPathSegment("")
            if (query.isNotBlank()) addQueryParameter("title", query.trim())
            filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotBlank() }?.let { addQueryParameter("author", it.trim()) }
            filters.firstInstanceOrNull<ArtistFilter>()?.state?.takeIf { it.isNotBlank() }?.let { addQueryParameter("artist", it.trim()) }
            addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "modified")
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("genre[]", it.value) }
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("a.bm-card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst(".bm-card__title")!!.text()
                thumbnail_url = card.selectFirst("img")?.imageUrl()
            }
        }
        return MangasPage(mangas, document.selectFirst("nav.bm-pager a.next") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = when {
            segments.size == 2 && segments[0] == "series" -> segments[1]
            segments.size == 1 && segments[0].contains("-chapter-") ->
                client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst("a.agc-reader-cover")
                    ?.absUrl("href")?.toHttpUrlOrNull()?.pathSegments?.getOrNull(1)
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = "/series/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        val details = parseMangaDetails(document).apply { url = manga.url }
        return SMangaUpdate(details) { if (fetchChapters) parseChapterList(document, mangaUrl) else chapters }
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.bm-series__title")!!.text()
        thumbnail_url = document.selectFirst(".bm-series__cover img")?.imageUrl()
        author = document.metaValue("Penulis")
        artist = document.metaValue("Artist")
        val type = document.selectFirst(".bm-series__badges .bm-badge:not(.bm-badge--status)")?.text()
        genre = (listOfNotNull(type) + document.select(".bm-chips a.bm-chip").map { it.text() })
            .distinctBy { it.lowercase() }
            .joinToString()
            .ifEmpty { null }
        status = when (document.selectFirst(".bm-series__badges .bm-badge--status")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(document.statValue("skor")?.toDoubleOrNull())
            .info(
                type,
                document.metaValue("Tahun Rilis"),
                document.statValue("dilihat")?.let { "$it dilihat" },
            )
            .description(document.selectFirst(".bm-synopsis__body"))
            .field("Serialisasi", document.metaValue("Serialisasi"))
            .altTitles(document.selectFirst(".bm-series__alt")?.text()?.split(",")?.map { it.trim() }.orEmpty(), "Judul Alternatif")
            .build()
    }

    private fun Document.metaValue(label: String) = selectFirst(".bm-meta div:has(> dt:containsOwn($label)) > dd")?.textOrNull()

    private fun Document.statValue(label: String) = selectFirst(".bm-series__stats div:has(> dt:containsOwn($label)) > dd")?.textOrNull()

    private suspend fun parseChapterList(document: Document, mangaUrl: String): List<SChapter> {
        val script = document.selectFirst("script:containsData(agcChapterListConfig)")!!.data()
        val initial = initialChaptersRegex.find(script)!!.groupValues[1].parseAs<List<ChapterDto>>()
        val total = totalRegex.find(script)!!.groupValues[1].toInt()
        val dates = initial.associate { it.id to it.date }
        val chapters = if (initial.size < total) {
            val url = "$baseUrl/wp-json/agc/v1/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("series", seriesIdRegex.find(script)!!.groupValues[1])
                .build()
            client.get(url, Fetch.XHR.from(mangaUrl)).parseAs<List<ChapterDto>>()
        } else {
            initial
        }
        return chapters.map { chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapter.url)
                name = "Chapter ${chapter.chapter}"
                date_upload = (chapter.date ?: dates[chapter.id])?.times(1000) ?: parseRelativeDate(chapter.time)
            }
        }
    }

    // human_time_diff, e.g. "4 days lalu", for rows without a timestamp
    private fun parseRelativeDate(time: String): Long {
        val (amount, unit) = relativeDateRegex.find(time.lowercase())?.destructured ?: return 0L
        val value = amount.toLong()
        val now = ZonedDateTime.now()
        val date = when {
            unit.startsWith("sec") || unit == "detik" -> now.minusSeconds(value)
            unit.startsWith("min") || unit == "menit" -> now.minusMinutes(value)
            unit.startsWith("hour") || unit == "jam" -> now.minusHours(value)
            unit.startsWith("day") || unit == "hari" -> now.minusDays(value)
            unit.startsWith("week") || unit == "minggu" -> now.minusWeeks(value)
            unit.startsWith("month") || unit == "bulan" -> now.minusMonths(value)
            unit.startsWith("year") || unit == "tahun" -> now.minusYears(value)
            else -> return 0L
        }
        return date.toInstant().toEpochMilli()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".reader-area img")
        .map { it.imageUrl()!! }
        .distinct()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    // image hosts link http, which only redirects to https
    private fun Element.imageUrl() = absUrl("src").toHttpUrlOrNull()?.newBuilder()?.scheme("https")?.build()?.toString()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/series/", Fetch.DOCUMENT).asJsoup()
        .select("#bm-filter-panel input[name='genre[]'][value~=.+]")
        .map { GenreDto(it.parent()!!.text(), it.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            add(TypeFilter())
            add(AuthorFilter())
            add(ArtistFilter())
            data?.parseAs<List<GenreDto>>()?.let { add(GenreFilter(it)) }
        },
    )

    companion object {
        private val initialChaptersRegex = Regex("""agcInitialChapters\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL)
        private val seriesIdRegex = Regex("""seriesId:\s*(\d+)""")
        private val totalRegex = Regex("""total:\s*(\d+)""")
        private val relativeDateRegex = Regex("""(\d+)\s*([a-z]+)""")
    }
}
