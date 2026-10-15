package eu.kanade.tachiyomi.extension.all.mangatoon

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaToon : KeiSource() {

    private val urlLang: String get() = if (lang == "zh") {
        "cn"
    } else if (lang == "pt-BR") {
        "pt"
    } else {
        lang
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1, 1.seconds)

    private val locale by lazy { Locale.forLanguageTag(lang) }

    private val lockedError = when (lang) {
        "pt-BR" ->
            "Este capítulo é pago e não pode ser lido. " +
                "Use o app oficial do MangaToon para comprar e ler."

        else ->
            "This chapter is paid and can't be read. " +
                "Use the MangaToon official app to purchase and read it."
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        // Portuguese website doesn't seem to have popular titles.
        val path = if (lang == "pt-BR") "comic" else "hot"
        return mangaListParse(client.get("$baseUrl/$urlLang/genre/$path?type=1&page=${page - 1}"))
    }

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.genre-content div.items a").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("span.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/$urlLang/genre/new?type=1&page=${page - 1}"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = "$baseUrl/$urlLang/search".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .build()
        val document = client.get(searchUrl).asJsoup()
        val mangas = document.select("div.comics-result div.recommend-item:has(a[abs:href^=$baseUrl])").map { element ->
            SManga.create().apply {
                title = element.select("div.recommend-comics-title").text()
                thumbnail_url = element.select("img").imgAttr().toNormalPosterUrl()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst("span.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.content-title").text()
        thumbnail_url = element.select("img").imgAttr().toNormalPosterUrl()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(manga, document), chapterListParse(document))
    }

    private fun mangaDetailsParse(manga: SManga, document: Document): SManga = SManga.create().apply {
        title = manga.title
        author = document.select("div.detail-author-name span").text()
            .substringAfter(": ")
        description = document.select("div.detail-description-short p")
            .joinToString("\n\n") { it.text() }
        genre = document.select("div.detail-tags-info span").text()
            .split("/")
            .map { it.capitalize(locale) }
            .sorted()
            .joinToString { it.trim() }
        status = document.select("div.detail-status").text().toStatus()
        val thumbnail = document.select("div.detail-img img").imgAttr().toNormalPosterUrl()
        if (!thumbnail.contains("cartoon-big-images")) {
            thumbnail_url = thumbnail
        }
    }

    // The page only renders a few episodes; the full list (with paid flags) is embedded as JSON.
    private fun chapterListParse(document: Document): List<SChapter> {
        val watchPath = document.selectFirst("a.episode-item-new")?.attr("href")
            ?.substringBeforeLast("/")
            ?: return emptyList()
        val episodes = document.select("script").firstNotNullOfOrNull { EPISODES_REGEX.find(it.data()) }
            ?.groupValues?.get(1)
            ?.replace(JS_ESCAPE_REGEX, "$1")
            ?.parseAs<List<EpisodeDto>?>()
            .orEmpty()

        return episodes.filterNot { it.isFee }.map {
            SChapter.create().apply {
                name = it.title
                chapter_number = it.weight
                date_upload = DATE_FORMAT.tryParseDate(it.openAt)
                setUrlWithoutDomain("$watchPath/${it.id}")
            }
        }.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(getChapterUrl(chapter)).asJsoup())

    private fun pageListParse(document: Document): List<Page> = document.select("div.pictures div img:first-child")
        .mapIndexed { i, element -> Page(i, imageUrl = element.imgAttr()) }
        .takeIf { it.isNotEmpty() } ?: throw Exception(lockedError)

    protected open fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    protected open fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    private fun String.toNormalPosterUrl(): String = replace(POSTER_SUFFIX, "$1")

    private fun String.toStatus(): Int = when (lowercase(locale)) {
        in ONGOING_STATUS -> SManga.ONGOING
        in COMPLETED_STATUS -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val ONGOING_STATUS = listOf(
            "连载", "on going", "sedang berlangsung", "tiếp tục cập nhật",
            "en proceso", "atualizando", "เซเรียล", "en cours", "連載中",
        )

        private val COMPLETED_STATUS = listOf(
            "完结",
            "completed",
            "tamat",
            "đã full",
            "terminada",
            "concluído",
            "จบ",
            "fin",
        )

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

        private val POSTER_SUFFIX = "(jpg)-poster(.*)\\d+?$".toRegex()

        private val EPISODES_REGEX = """data = JSON\.parse\('(.*)'\);""".toRegex()

        private val JS_ESCAPE_REGEX = """\\(["'])""".toRegex()
    }
}
