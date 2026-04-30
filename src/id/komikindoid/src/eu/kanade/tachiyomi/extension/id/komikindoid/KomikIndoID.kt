package eu.kanade.tachiyomi.extension.id.komikindoid

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class KomikIndoID : HttpSource() {
    override val name = "KomikIndoID"
    override val baseUrl = "https://komikindo.ch"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/daftar-manga/page/$page/?order=popular", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/daftar-manga/page/$page/?order=update", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.animepost").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("div.limit img")?.attr("abs:src")
                title = element.selectFirst("div.tt h3")?.text()
                    ?: throw Exception("Missing title")
                element.selectFirst("div.animposx > a")?.let {
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
            }
        }
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/daftar-manga/page/$page/".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if (filter.state.isNotEmpty()) {
                        url.addQueryParameter("author", filter.state)
                    }
                }
                is YearFilter -> {
                    if (filter.state.isNotEmpty()) {
                        url.addQueryParameter("yearx", filter.state)
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is OriginalLanguageFilter -> {
                    filter.state.forEach { lang ->
                        if (lang.state) {
                            url.addQueryParameter("type[]", lang.id)
                        }
                    }
                }
                is FormatFilter -> {
                    filter.state.forEach { format ->
                        if (format.state) {
                            url.addQueryParameter("format[]", format.id)
                        }
                    }
                }
                is DemographicFilter -> {
                    filter.state.forEach { demographic ->
                        if (demographic.state) {
                            url.addQueryParameter("demografis[]", demographic.id)
                        }
                    }
                }
                is StatusFilter -> {
                    filter.state.forEach { status ->
                        if (status.state) {
                            url.addQueryParameter("status[]", status.id)
                        }
                    }
                }
                is ContentRatingFilter -> {
                    filter.state.forEach { rating ->
                        if (rating.state) {
                            url.addQueryParameter("konten[]", rating.id)
                        }
                    }
                }
                is ThemeFilter -> {
                    filter.state.forEach { theme ->
                        if (theme.state) {
                            url.addQueryParameter("tema[]", theme.id)
                        }
                    }
                }
                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.state) {
                            url.addQueryParameter("genre[]", genre.id)
                        }
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.infoanime") ?: return SManga.create()
        val descElement = document.selectFirst("div.desc > .entry-content.entry-content-single")

        return SManga.create().apply {
            val authorCleaner = document.selectFirst(".infox .spe b:contains(Pengarang)")?.text() ?: ""
            author = document.selectFirst(".infox .spe span:contains(Pengarang)")?.text()?.substringAfter(authorCleaner)

            val artistCleaner = document.selectFirst(".infox .spe b:contains(Ilustrator)")?.text() ?: ""
            artist = document.selectFirst(".infox .spe span:contains(Ilustrator)")?.text()?.substringAfter(artistCleaner)

            genre = infoElement.select(".infox .genre-info a, .infox .spe span:contains(Grafis:) a, .infox .spe span:contains(Tema:) a, .infox .spe span:contains(Konten:) a, .infox .spe span:contains(Jenis Komik:) a")
                .joinToString(", ") { it.text() }

            status = parseStatus(infoElement.selectFirst(".infox > .spe > span:nth-child(2)")?.text() ?: "")

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

            thumbnail_url = document.selectFirst(".thumb > img:nth-child(1)")?.attr("abs:src")?.substringBeforeLast("?")
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("berjalan", true) -> SManga.ONGOING
        element.contains("tamat", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapter_list li").map { element ->
            SChapter.create().apply {
                val urlElement = element.selectFirst(".lchx a")!!
                setUrlWithoutDomain(urlElement.attr("abs:href"))
                name = urlElement.text()

                CHAPTER_REGEX.find(name)?.let {
                    chapter_number = it.groups[1]?.value?.toFloatOrNull() ?: -1f
                }

                date_upload = element.selectFirst(".dt a")?.text()?.let { parseChapterDate(it) } ?: 0L
            }
        }
    }

    private fun parseChapterDate(date: String): Long = if (date.contains("yang lalu")) {
        val value = date.split(' ').firstOrNull()?.toIntOrNull() ?: return 0L
        when {
            "detik" in date -> Calendar.getInstance().apply { add(Calendar.SECOND, -value) }.timeInMillis
            "menit" in date -> Calendar.getInstance().apply { add(Calendar.MINUTE, -value) }.timeInMillis
            "jam" in date -> Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -value) }.timeInMillis
            "hari" in date -> Calendar.getInstance().apply { add(Calendar.DATE, -value) }.timeInMillis
            "minggu" in date -> Calendar.getInstance().apply { add(Calendar.DATE, -value * 7) }.timeInMillis
            "bulan" in date -> Calendar.getInstance().apply { add(Calendar.MONTH, -value) }.timeInMillis
            "tahun" in date -> Calendar.getInstance().apply { add(Calendar.YEAR, -value) }.timeInMillis
            else -> 0L
        }
    } else {
        dateFormat.tryParse(date)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.img-landmine img")
            .map { it.attr("onError").substringAfter("src='").substringBefore("';") }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
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

    companion object {
        private val CHAPTER_REGEX = Regex("""Chapter\s([0-9]+)""")
    }
}
