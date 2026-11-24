package eu.kanade.tachiyomi.extension.all.saymanhwa

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class SayManhwa : KeiSource() {
    private val saymanhwaLang: String
        get() = when (lang) {
            "pt" -> "pt-br"
            "zh", "zh-Hans" -> "zh-cn"
            "zh-Hant" -> "zh-tw"
            else -> lang
        }

    // Guests may open a new chapter once every 5 seconds (429 otherwise), 6 leaves room for network delays
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 1, period = 6.seconds) { it.isSite() && it.pathSegments.last().startsWith("chapter-") }
        .rateLimit(permits = 3) { it.isSite() }

    private fun HttpUrl.isSite() = host == baseUrl.toHttpUrl().host

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/$saymanhwaLang/popular".toHttpUrl().newBuilder()
            .apply { if (page != 1) addQueryParameter("page", page.toString()) }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangaPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/$saymanhwaLang/latest".toHttpUrl().newBuilder()
            .apply { if (page != 1) addQueryParameter("page", page.toString()) }
            .build()
        return parseMangaPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val selectedGenre = genreFilter?.vals?.getOrNull(genreFilter.state)

        val url = "$baseUrl/$saymanhwaLang".toHttpUrl().newBuilder().apply {
            if (selectedGenre.isNullOrEmpty()) {
                addPathSegment("series")
            } else {
                addPathSegment("genres").addPathSegment(selectedGenre)
            }
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            if (page != 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun parseMangaPage(document: Document): MangasPage {
        val mangas = document
            .select("article.series-card")
            .map(::mangaFromElement)

        // The page number list has a link after a span too, even on the last page
        val hasNextPage = document.selectFirst("nav.pagination > span + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val mangaLink = element.selectFirst(".series-card-body h2 a")!!
        setUrlWithoutDomain(mangaLink.absUrl("href"))
        title = mangaLink.text()
        thumbnail_url = element.selectFirst(".series-card-cover img, a img")?.absUrl("src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document, getMangaUrl(manga)),
            chapterListParse(document),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        // Series and chapter links, with any language or none, open the series in this source's language
        val slug = url.pathSegments.dropWhile { it != "series" }.getOrNull(1)?.ifEmpty { null } ?: return null
        val mangaUrl = "$baseUrl/$saymanhwaLang/series/$slug"
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        return mangaDetailsParse(document, mangaUrl).apply { initialized = true }
    }

    private fun mangaDetailsParse(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        setUrlWithoutDomain(mangaUrl)
        title = document.selectFirst("h1")!!.ownText()
        thumbnail_url = document.selectFirst(".series-v72-cover img")?.absUrl("src")

        val creatorRows = document.select(".series-v72-meta-row strong.series-creator-links")
        author = creatorRows.getOrNull(0)?.creators()
        artist = creatorRows.getOrNull(1)?.creators()

        status = document.selectFirst(".series-v72-meta-pair > div:first-child strong")?.ownText().toStatus(saymanhwaLang)

        val type = document.selectFirst(".series-v72-meta-pair > div:nth-child(2) strong")?.ownText()
        val genresContent = document.select(".series-v72-genres a").map { it.text() }
        genre = (listOfNotNull(type) + genresContent)
            .distinct()
            .joinToString()
            .ifEmpty { null }

        val altTitles = document.selectFirst(".series-v72-alt")?.text()?.let { altText ->
            val separator = ALT_TITLE_SEPARATORS.firstOrNull { it in altText } ?: ", "
            altText.split(separator).filterNot { it.isPlaceholder() || it == title }
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".series-v72-description"))
            .altTitles(altTitles.orEmpty())
            .build()
    }

    private fun Element.creators(): String? = select("a")
        .map { it.text() }
        .filterNot { it.isPlaceholder() }
        .joinToString()
        .ifEmpty { null }

    // The site's placeholder for an unknown author or title
    private fun String.isPlaceholder() = equals("updating", ignoreCase = true)

    private fun String?.toStatus(langPath: String): Int {
        val text = this
        if (text.isNullOrEmpty()) return SManga.UNKNOWN

        val (ongoingWords, completedWords) = when (langPath) {
            "ar" -> listOf("مستمر") to listOf("مكتمل")
            "de" -> listOf("laufend") to listOf("abgeschlossen")
            "es" -> listOf("en curso") to listOf("completado")
            "fil" -> listOf("tuloy-tuloy", "tuloy tuloy") to listOf("tapos")
            "fr" -> listOf("en cours") to listOf("terminé", "termine")
            "id" -> listOf("berjalan") to listOf("selesai")
            "ja" -> listOf("連載中") to listOf("完結")
            "pt-br" -> listOf("em andamento") to listOf("concluído", "concluido")
            "th" -> listOf("กำลังดำเนิน") to listOf("จบแล้ว")
            "vi" -> listOf("đang tiến hành", "đang") to listOf("hoàn thành")
            "zh-cn", "zh-tw" -> listOf("连载中", "連載中") to listOf("已完结", "已完結", "完结", "完結")
            else -> listOf("ongoing") to listOf("completed")
        }

        return when {
            ongoingWords.any { text.contains(it, ignoreCase = true) } -> SManga.ONGOING
            completedWords.any { text.contains(it, ignoreCase = true) } -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun chapterListParse(document: Document): List<SChapter> = document
        .select(".series-v72-chapter-list > a.series-v72-chapter-row")
        .map(::chapterFromElement)

    private fun chapterFromElement(element: Element): SChapter {
        val chapterName = element.selectFirst(".series-chapter-number-text")!!.ownText()
        val isVip = element.selectFirst(".chapter-mini-lock") != null
        val dateStr = element.selectFirst("time")?.attr("datetime")
        return SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = if (isVip) "🔒 " + chapterName else chapterName
            date_upload = Instant.tryParse(dateStr)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".reader-pages > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.select(".recommend-card-v71").mapNotNull { element ->
            val link = element.selectFirst("h3 a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/$saymanhwaLang/series".toHttpUrl()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val genres = document.select("select[name=genre] option").mapNotNull { option ->
            val value = option.attr("value")
            val text = option.text()
            if (value.isEmpty() || text.isEmpty()) {
                null
            } else {
                GenreOption(text, value)
            }
        }
        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>()
        return if (genres.isNullOrEmpty()) {
            FilterList()
        } else {
            FilterList(GenreFilter("Genre", genres))
        }
    }

    companion object {
        // Some series list their titles with these instead of commas, which titles can contain
        private val ALT_TITLE_SEPARATORS = listOf(" • ", " / ")
    }
}
