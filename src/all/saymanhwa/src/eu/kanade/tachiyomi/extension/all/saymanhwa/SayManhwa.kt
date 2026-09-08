package eu.kanade.tachiyomi.extension.all.saymanhwa

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class SayManhwa : KeiSource() {
    private val saymanhwaLang: String
        get() = when (lang) {
            "pt" -> "pt-br"
            "zh" -> "zh-cn"
            else -> lang
        }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/$saymanhwaLang/popular".toHttpUrl().newBuilder()
            .apply { if (page != 1) addQueryParameter("page", page.toString()) }
            .build()
        val document = client.get(url).asJsoup()
        return parseMangaPage(document, page)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/$saymanhwaLang/latest".toHttpUrl().newBuilder()
            .apply { if (page != 1) addQueryParameter("page", page.toString()) }
            .build()
        return parseMangaPage(client.get(url).asJsoup(), page)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val selectedGenre = genreFilter?.vals?.getOrNull(genreFilter.state)

        val url = "$baseUrl/$saymanhwaLang/series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            if (!selectedGenre.isNullOrEmpty()) {
                addQueryParameter("genre", selectedGenre)
            }
            if (page != 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return parseMangaPage(client.get(url).asJsoup(), page)
    }

    private fun parseMangaPage(document: Document, page: Int): MangasPage {
        val mangas = document
            .select("article.series-card")
            .map(::mangaFromElement)

        val hasNextPage = document.select("nav.pagination span + a").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val mangaLink = element.selectFirst(".series-card-body h2 a")!!
        setUrlWithoutDomain(mangaLink.absUrl("href"))
        title = mangaLink.text()
        thumbnail_url = element.selectFirst(".series-card-cover img, a img")?.absUrl("src")
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document, getMangaUrl(manga)),
            chapterListParse(document),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val mangaUrl = mangaUrlFrom(url)
        val document = client.get(mangaUrl).asJsoup()
        return mangaDetailsParse(document, mangaUrl.toString()).apply { initialized = true }
    }

    private fun mangaUrlFrom(url: HttpUrl): HttpUrl {
        val segments = url.pathSegments
        if (segments.lastOrNull()?.startsWith("chapter-") == true) {
            return url.newBuilder().removePathSegment(segments.size - 1).build()
        }
        return url
    }

    private fun mangaDetailsParse(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        setUrlWithoutDomain(mangaUrl.substringBefore("/chapter-"))
        title = document.selectFirst("h1")?.ownText()!!
        thumbnail_url = document.selectFirst(".series-v72-cover img")?.absUrl("src")

        val creatorRows = document.select(".series-v72-meta-row strong.series-creator-links")
        author = creatorRows.getOrNull(0)?.select("a")?.joinToString { it.text() }
        artist = creatorRows.getOrNull(1)?.select("a")?.joinToString { it.text() }

        status = document.selectFirst(".series-v72-meta-pair > div:first-child strong")?.ownText().toStatus(saymanhwaLang)

        val type = document.selectFirst(".series-v72-meta-pair > div:nth-child(2) strong")?.ownText()
        val genresContent = document.select("a[href*='/genres/']").map { it.text() }
        genre = (listOfNotNull(type) + genresContent)
            .distinct()
            .joinToString()

        description = document.selectFirst(".series-seo-context p")?.wholeText().orEmpty()
    }

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

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".reader-pages img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    // ============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return document.select(".recommend-card-v71").mapNotNull { element ->
            val link = element.selectFirst("h3 a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/$saymanhwaLang/series".toHttpUrl()
        val document = client.get(url).asJsoup()
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

    @Serializable
    class GenreOption(
        val name: String,
        val value: String?,
    )

    class GenreFilter(title: String, genres: List<GenreOption>) :
        Filter.Select<String>(
            title,
            arrayOf("All") + genres.map { it.name }.toTypedArray(),
        ) {
        val vals = arrayOf<String?>(null) + genres.map { it.value }.toTypedArray()
    }
}
