package eu.kanade.tachiyomi.extension.id.komikindoid

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class KomikIndoID : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val chapterRegex = Regex("""Chapter\s+([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(12, 3.seconds)

    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/daftar-manga/${pagePath(page)}?order=popular").asJsoup()
        return mangaListParse(document)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/daftar-manga/${pagePath(page)}?order=update").asJsoup()
        return mangaListParse(document)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/daftar-manga/${pagePath(page)}".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("title", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> {
                        if (filter.state.isNotEmpty()) {
                            addQueryParameter("author", filter.state)
                        }
                    }
                    is YearFilter -> {
                        if (filter.state.isNotEmpty()) {
                            addQueryParameter("yearx", filter.state)
                        }
                    }
                    is SortFilter -> {
                        addQueryParameter("order", filter.toUriPart())
                    }
                    is OriginalLanguageFilter -> {
                        filter.state.forEach { lang ->
                            if (lang.state) {
                                addQueryParameter("type[]", lang.id)
                            }
                        }
                    }
                    is FormatFilter -> {
                        filter.state.forEach { format ->
                            if (format.state) {
                                addQueryParameter("format[]", format.id)
                            }
                        }
                    }
                    is DemographicFilter -> {
                        filter.state.forEach { demographic ->
                            if (demographic.state) {
                                addQueryParameter("demografis[]", demographic.id)
                            }
                        }
                    }
                    is StatusFilter -> {
                        filter.state.forEach { status ->
                            if (status.state) {
                                addQueryParameter("status[]", status.id)
                            }
                        }
                    }
                    is ContentRatingFilter -> {
                        filter.state.forEach { rating ->
                            if (rating.state) {
                                addQueryParameter("konten[]", rating.id)
                            }
                        }
                    }
                    is ThemeFilter -> {
                        filter.state.forEach { theme ->
                            if (theme.state) {
                                addQueryParameter("tema[]", theme.id)
                            }
                        }
                    }
                    is GenreFilter -> {
                        filter.state.forEach { genre ->
                            if (genre.state) {
                                addQueryParameter("genre[]", genre.id)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        val document = client.get(url).asJsoup()
        return mangaListParse(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "komik") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val targetUrl = "$baseUrl/komik/$slug/".toHttpUrl()
        val document = client.get(targetUrl).asJsoup()
        val manga = SManga.create().apply {
            setUrlWithoutDomain(targetUrl.toString())
        }
        return parseDetails(document, manga)
    }

    // ======================= Details and Chapters ==========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document, manga),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = manga.apply {
        val infoElement = document.selectFirst("div.infoanime")
        val descElement = document.selectFirst("div.desc > .entry-content.entry-content-single")

        if (title.isEmpty()) {
            val parsedTitle = document.selectFirst("h1.entry-title")?.text()?.removePrefix("Komik")?.trim()
            if (!parsedTitle.isNullOrEmpty()) {
                title = parsedTitle
            }
        }

        val authorCleaner = document.selectFirst(".infox .spe b:contains(Pengarang)")?.text().orEmpty()
        author = document.selectFirst(".infox .spe span:contains(Pengarang)")?.text()?.substringAfter(authorCleaner)?.trim()

        val artistCleaner = document.selectFirst(".infox .spe b:contains(Ilustrator)")?.text().orEmpty()
        artist = document.selectFirst(".infox .spe span:contains(Ilustrator)")?.text()?.substringAfter(artistCleaner)?.trim()

        genre = infoElement?.select(".infox .genre-info a, .infox .spe span:contains(Grafis:) a, .infox .spe span:contains(Tema:) a, .infox .spe span:contains(Konten:) a, .infox .spe span:contains(Jenis Komik:) a")
            ?.joinToString { it.text() }

        status = parseStatus(infoElement?.selectFirst(".infox > .spe > span:nth-child(2)")?.text())

        description = buildString {
            val mainDesc = descElement?.select("p")?.text()?.substringAfter("bercerita tentang ")
            if (!mainDesc.isNullOrEmpty()) {
                append(mainDesc)
            }
            val altName = document.selectFirst(".infox > .spe > span:nth-child(1)")?.text()
            if (!altName.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(altName)
            }
        }

        thumbnail_url = document.selectFirst(".thumb > img:nth-child(1), .thumb img")?.imgAttr()?.substringBeforeLast("?")
    }

    private fun parseStatus(status: String?): Int {
        val s = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "berjalan" in s || "ongoing" in s -> SManga.ONGOING
            "tamat" in s || "completed" in s -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("#chapter_list li").map { element ->
        val urlElement = element.selectFirst(".lchx a")!!
        SChapter.create().apply {
            setUrlWithoutDomain(urlElement.absUrl("href"))
            name = urlElement.text()

            chapterRegex.find(name)?.let {
                chapter_number = it.groupValues[1].toFloatOrNull() ?: -1f
            }

            date_upload = element.selectFirst(".dt a")?.text()?.let { parseChapterDate(it) } ?: 0L
        }
    }

    private fun parseChapterDate(date: String): Long = if (date.contains("yang lalu")) {
        val value = date.split(' ').firstOrNull()?.trim()?.toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance()
        when {
            "detik" in date -> calendar.add(Calendar.SECOND, -value)
            "menit" in date -> calendar.add(Calendar.MINUTE, -value)
            "jam" in date -> calendar.add(Calendar.HOUR_OF_DAY, -value)
            "hari" in date -> calendar.add(Calendar.DATE, -value)
            "minggu" in date -> calendar.add(Calendar.DATE, -value * 7)
            "bulan" in date -> calendar.add(Calendar.MONTH, -value)
            "tahun" in date -> calendar.add(Calendar.YEAR, -value)
            else -> return 0L
        }
        calendar.timeInMillis
    } else {
        dateFormat.tryParseDate(date, ZoneId.of("Asia/Jakarta"))
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = baseUrl + chapter.url
        val document = client.get(chapterUrl).asJsoup()
        return document.select("div.img-landmine img")
            .mapNotNull { element ->
                val onerror = element.attr("onError")
                val url = if (onerror.contains("src='")) {
                    onerror.substringAfter("src='").substringBefore("';")
                } else {
                    element.imgAttr()
                }
                url.takeIf { it.isNotEmpty() }
            }
            .mapIndexed { index, url ->
                Page(index, chapterUrl, imageUrl = url)
            }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        Filter.Header("NOTE: Ignored if using text search!"),
        AuthorFilter(),
        YearFilter(),
        Filter.Separator(),
        OriginalLanguageFilter(getOriginalLanguage()),
        FormatFilter(getFormat()),
        DemographicFilter(getDemographic()),
        StatusFilter(getStatus()),
        ContentRatingFilter(getContentRating()),
        ThemeFilter(getTheme()),
        GenreFilter(getGenre()),
    )

    // ============================= Utilities ==============================
    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("div.animepost").map { element ->
            SManga.create().apply {
                title = element.selectFirst("div.tt h3")?.text().orEmpty().ifEmpty {
                    element.selectFirst("div.animposx > a")!!.attr("title").removePrefix("Komik").trim()
                }
                element.selectFirst("div.animposx > a")?.let {
                    setUrlWithoutDomain(it.absUrl("href"))
                }
                thumbnail_url = element.selectFirst("div.limit img")?.imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }
}
