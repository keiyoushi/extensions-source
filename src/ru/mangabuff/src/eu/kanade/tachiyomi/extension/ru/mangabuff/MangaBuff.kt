package eu.kanade.tachiyomi.extension.ru.mangabuff

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBuff : ParsedHttpSource() {
    override val baseUrl = "https://mangabuff.ru"
    override val lang = "ru"
    override val name = "MangaBuff"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::tokenInterceptor)
        .build()

    private val json: Json by injectLazy()

    // From Akuma - CSRF token
    private var storedToken: String? = null

    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method == "POST" && request.header("X-CSRF-TOKEN") == null) {
            val newRequest = request.newBuilder()
            val token = getToken()
            val response = chain.proceed(
                newRequest
                    .addHeader("X-CSRF-TOKEN", token)
                    .build(),
            )

            if (response.code == 419) {
                response.close()
                storedToken = null // reset the token
                val newToken = getToken()
                return chain.proceed(
                    newRequest
                        .addHeader("X-CSRF-TOKEN", newToken)
                        .build(),
                )
            }

            return response
        }

        val response = chain.proceed(request)

        if (response.header("Content-Type")?.contains("text/html") != true) {
            return response
        }

        storedToken = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
            .selectFirst("head meta[name*=csrf-token]")
            ?.attr("content")

        return response
    }

    private fun getToken(): String {
        if (storedToken.isNullOrEmpty()) {
            val request = GET(baseUrl, headers)
            client.newCall(request).execute().close() // updates token in interceptor
        }
        return storedToken!!
    }

    // Popular
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.POPULAR)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.LATEST)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (!query.startsWith(SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val request = GET("$baseUrl/manga/${query.substringAfter(SEARCH_PREFIX)}")
        return client.newCall(request).asObservableSuccess().map { response ->
            val details = mangaDetailsParse(response)
            details.setUrlWithoutDomain(request.url.toString())
            MangasPage(listOf(details), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                if (page != 1) addQueryParameter("page", page.toString())
            }.build()

            return GET(url, headers)
        }

        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            (filters.find { it is GenreFilter } as? GenreFilter)?.let { filter ->
                filter.included?.forEach { addQueryParameter("genres[]", it) }
            }
            (filters.find { it is TypeFilter } as? TypeFilter)?.let { filter ->
                filter.included?.forEach { addQueryParameter("type_id[]", it) }
            }
            (filters.find { it is TagFilter } as? TagFilter)?.let { filter ->
                filter.included?.forEach { addQueryParameter("tags[]", it) }
            }
            (filters.find { it is StatusFilter } as? StatusFilter)?.let { filter ->
                filter.checked?.forEach { addQueryParameter("status_id[]", it) }
            }
            (filters.find { it is AgeFilter } as? AgeFilter)?.let { filter ->
                filter.checked?.forEach { addQueryParameter("age_rating[]", it) }
            }
            (filters.find { it is RatingFilter } as? RatingFilter)?.let { filter ->
                filter.checked?.forEach { addQueryParameter("rating[]", it) }
            }
            (filters.find { it is YearFilter } as? YearFilter)?.let { filter ->
                filter.checked?.forEach { addQueryParameter("year[]", it) }
            }
            (filters.find { it is ChapterCountFilter } as? ChapterCountFilter)?.let { filter ->
                filter.checked?.forEach { addQueryParameter("chapters[]", it) }
            }
            (filters.find { it is GenreFilter } as? GenreFilter)?.let { filter ->
                filter.excluded?.forEach { addQueryParameter("without_genres[]", it) }
            }
            (filters.find { it is TypeFilter } as? TypeFilter)?.let { filter ->
                filter.excluded?.forEach { addQueryParameter("without_type_id[]", it) }
            }
            (filters.find { it is TagFilter } as? TagFilter)?.let { filter ->
                filter.excluded?.forEach { addQueryParameter("without_tags[]", it) }
            }
            (filters.find { it is SortFilter } as? SortFilter)?.let { filter ->
                addQueryParameter("sort", filter.selected)
            }
            if (page != 1) addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".cards .cards__item"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".cards__name")!!.text()

        val slug = "$baseUrl$url".toHttpUrl().pathSegments.last()
        thumbnail_url = "$baseUrl/img/manga/posters/$slug.jpg"
    }

    override fun searchMangaNextPageSelector() =
        ".pagination .pagination__button--active + li:not(:last-child)"

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1, .manga__name, .manga-mobile__name")!!.text()

        description = buildString {
            document
                .selectFirst(".manga__description")
                ?.text()
                ?.also { append(it) }

            document // rating%
                .selectFirst(".manga__rating")
                ?.text()
                ?.toDoubleOrNull()
                ?.let { it / 10.0 }
                ?.also {
                    if (isNotEmpty()) append("\n\n")
                    append(String.format(Locale("ru"), "Рейтинг: %.0f%%", it * 100))
                }

            document // views
                .selectFirst(".manga__views")
                ?.text()
                ?.replace(" ", "")
                ?.toIntOrNull()
                ?.also {
                    if (isNotEmpty()) append("\n\n")
                    append(String.format(Locale("ru"), "Просмотров: %,d", it))
                }

            document // favorites
                .selectFirst(".manga")
                ?.attr("data-fav-count")
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?.also {
                    if (isNotEmpty()) append("\n\n")
                    append(String.format(Locale("ru"), "Избранное: %,d", it))
                }

            document // alternative names
                .select(".manga__name-alt > span, .manga-mobile__name-alt > span")
                .eachText()
                .takeIf { it.isNotEmpty() }
                ?.also {
                    if (isNotEmpty()) append("\n\n")
                    append("Альтернативные названия:\n")
                    append(it.joinToString("\n") { "• $it" })
                }
        }

        genre = buildList {
            addAll(document.select(".manga__middle-links > a:not(:last-child)").eachText())
            addAll(document.select(".manga-mobile__info > a:not(:last-child)").eachText())
            addAll(document.select(".tags > .tags__item").eachText())
        }.takeIf { it.isNotEmpty() }?.joinToString()

        status = document
            .select(".manga__middle-links > a:last-child, .manga-mobile__info > a:last-child")
            .text()
            .parseStatus()

        thumbnail_url = document
            .selectFirst(".manga__img img, img.manga-mobile__image")
            ?.absUrl("src")
    }

    // Chapters
    override fun chapterListSelector() = "a.chapters__item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.select(".chapters__volume, .chapters__value, .chapters__name").text()
        date_upload = runCatching {
            dateFormat.parse(element.selectFirst(".chapters__add-date")!!.text())!!.time
        }.getOrDefault(0L)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())

        val chapters = super.chapterListParse(response)

        // HTML only shows 100 entries. If this class is present it will load more via API
        if (document.selectFirst(".load-chapters-trigger") == null) {
            return chapters
        }

        val mangaId = document.selectFirst(".manga")?.attr("data-id")
            ?: throw Exception("Не удалось найти ID манги")

        val form = FormBody.Builder()
            .add("manga_id", mangaId)
            .build()

        val moreChapters = client
            .newCall(POST("$baseUrl/chapters/load", headers, form))
            .execute()
            .parseAs<WrappedHtmlDto>()
            .content
            .let(Jsoup::parseBodyFragment)
            .select(chapterListSelector())
            .map(::chapterFromElement)

        return chapters + moreChapters
    }

    // Pages
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".reader__pages img").mapIndexed { i, img ->
            Page(i, document.location(), img.imgAttr())
        }
    }

    // Other
    override fun getFilterList() = FilterList(
        Filter.Header("ПРИМЕЧАНИЕ: Игнорируется, если используется поиск по тексту!"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
        TypeFilter(),
        TagFilter(),
        StatusFilter(),
        AgeFilter(),
        RatingFilter(),
        YearFilter(),
        ChapterCountFilter(),
    )

    private fun String.parseStatus(): Int = when (this.lowercase()) {
        "завершен" -> SManga.COMPLETED
        "продолжается" -> SManga.ONGOING
        "заморожен" -> SManga.ON_HIATUS
        "заброшен" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    @Serializable
    class WrappedHtmlDto(
        val content: String,
    )

    companion object {
        const val SEARCH_PREFIX = "slug:"
        private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
    }
}
