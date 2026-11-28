package eu.kanade.tachiyomi.multisrc.komikindo

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
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

abstract class KomikIndo : KeiSource() {

    protected open val mangaListPath = "daftar-manga"

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(12, 3.seconds)

    override suspend fun getPopularManga(page: Int) = getMangaList(page) { addQueryParameter("order", "popular") }

    override suspend fun getLatestUpdates(page: Int) = getMangaList(page) { addQueryParameter("order", "update") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getMangaList(page) {
        if (query.isNotBlank()) addQueryParameter("title", query.trim())
        filters.filterIsInstance<UrlPartFilter>().forEach { it.addUrlParameter(this) }
    }

    private suspend fun getMangaList(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val pagePath = if (page > 1) "page/$page/" else ""
        val url = "$baseUrl/$mangaListPath/$pagePath".toHttpUrl().newBuilder().apply(params).build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.animepost").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("div.animposx > a")!!.absUrl("href"))
                title = element.selectFirst(".tt")!!.text()
                thumbnail_url = element.selectFirst("div.limit img")?.imgAttr()
            }
        }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "komik") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/komik/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = parseDetails(document).apply { url = manga.url }
        return SMangaUpdate(details) { if (fetchChapters) parseChapters(document) else chapters }
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")!!.text().removePrefix("Komik").trim()
        thumbnail_url = document.selectFirst(".thumb img")?.imgAttr()?.substringBefore("?")
        author = document.info("Author", "Pengarang")
        artist = document.info("Artis", "Ilustrator")
        genre = document.select(".infox .genre-info a, .infox .spe span:has(> b:matchesOwn(^(Grafis|Tema|Konten|Jenis Komik):)) a")
            .joinToString { it.text() }
        status = parseStatus(document.info("Status"))
        description = MarkdownDescription.Builder()
            .description(document.select("div.desc > .entry-content-single p").text().substringAfter("bercerita tentang "))
            .altTitles(document.info("Judul Alternatif")?.split(", ").orEmpty(), "Judul Alternatif")
            .build()
    }

    private fun Document.info(vararg labels: String) = labels.firstNotNullOfOrNull { selectFirst(".infox .spe span:has(> b:containsOwn($it))") }
        ?.text()?.substringAfter(':')?.trim()?.ifEmpty { null }

    private fun parseStatus(status: String?): Int {
        val s = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "berjalan" in s || "ongoing" in s -> SManga.ONGOING
            "tamat" in s || "completed" in s -> SManga.COMPLETED
            "hiatus" in s -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document) = document.select("#chapter_list li").map { element ->
        val link = element.selectFirst(".lchx a")!!
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = element.selectFirst(".dt a")?.text()?.let(::parseChapterDate) ?: 0L
        }
    }

    private fun parseChapterDate(date: String): Long = if (date.contains("yang lalu")) {
        val value = date.substringBefore(' ').trim().toIntOrNull() ?: return 0L
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

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("div:has(> img[alt*=Chapter]) > img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.imgAttr()) }

    private fun Element.imgAttr(): String = if (hasAttr("data-lazy-src")) absUrl("data-lazy-src") else absUrl("src")

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/$mangaListPath/", Fetch.DOCUMENT).asJsoup()
        .select("form.filters div.filter.dropdown")
        .mapNotNull { group ->
            val param = group.selectFirst("input")?.attr("name") ?: return@mapNotNull null
            val options = group.select("li:has(> input)").map { it.text() to it.selectFirst("input")!!.attr("value") }
            FilterGroup(group.selectFirst("button")!!.ownText().trim(), param, options)
        }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        data?.parseAs<List<FilterGroup>>().orEmpty().map {
            if (it.param.endsWith("[]")) CheckBoxGroup(it.name, it.options, it.param) else SelectFilter(it.name, it.options, it.param)
        },
    )
}
