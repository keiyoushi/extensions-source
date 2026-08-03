package eu.kanade.tachiyomi.extension.ru.mangabuff

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaBuff :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::tokenInterceptor)
        addInterceptor(::gifToWebpInterceptor)
    }

    // From Akuma - CSRF token
    private var storedToken: String? = null
    private val preferences by getPreferencesLazy()

    // ============================== Interceptors ===============================
    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method == "POST" && request.header("X-CSRF-TOKEN") == null) {
            val token = getToken()
            val newRequest = request.newBuilder()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-CSRF-TOKEN", token)
                .build()

            val response = chain.proceed(newRequest)

            if (response.code == 419) {
                response.close()
                storedToken = null
                val retryToken = getToken()
                val retryRequest = request.newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("X-CSRF-TOKEN", retryToken)
                    .build()
                return chain.proceed(retryRequest)
            }

            return response
        }

        return chain.proceed(request)
    }

    private fun gifToWebpInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (!imgConvert()) return response

        val isGif = response.body.contentType()?.subtype?.lowercase() == "gif" ||
            response.request.url.pathSegments.lastOrNull()?.endsWith(".gif", ignoreCase = true) == true

        if (!isGif) return response

        val original = response.body.use { body ->
            BitmapFactory.decodeStream(body.byteStream())
                ?: throw IOException("Failed to decode GIF")
        }

        val buffer = Buffer()
        original.compress(Bitmap.CompressFormat.WEBP, 90, buffer.outputStream())
        original.recycle()
        return response.newBuilder().body(buffer.asResponseBody(WEBP_MEDIA_TYPE, buffer.size)).build()
    }

    private fun getToken(): String {
        storedToken?.let { return it }

        val request = GET(baseUrl, headers)
        val response = client.newCall(request).execute()

        response.use {
            val document = it.asJsoup()
            val token = document.select("head meta[name*=csrf-token]")
                .attr("content")

            if (token.isEmpty()) {
                throw IOException("Unable to find CSRF token")
            }

            storedToken = token
            return token
        }
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest(page, "real_views")

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest(page, "updated_at")

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            if (query.length < 3) {
                throw Exception("Запрос должен содержать не менее 3 символов. / The query must contain at least 3 characters")
            }
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("type", "manga")
                addQueryParameter("q", query)
                if (page > 1) {
                    addQueryParameter("page", page.toString())
                }
            }.build()

            client.get(url).use {
                return parseSearchManga(it.asJsoup())
            }
        }

        return makeCatalogRequest(page, "real_views", filters)
    }

    // ============================== Search Utilities ===============================
    private suspend fun makeCatalogRequest(page: Int, sortBy: String, filters: FilterList? = null): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            filters?.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.included?.forEach { addQueryParameter("genres[]", it) }
                        filter.excluded?.forEach { addQueryParameter("without_genres[]", it) }
                    }
                    is TypeFilter -> {
                        filter.included?.forEach { addQueryParameter("type_id[]", it) }
                        filter.excluded?.forEach { addQueryParameter("without_type_id[]", it) }
                    }
                    is TagFilter -> {
                        filter.included?.forEach { addQueryParameter("tags[]", it) }
                        filter.excluded?.forEach { addQueryParameter("without_tags[]", it) }
                    }
                    is StatusFilter -> filter.checked?.forEach { addQueryParameter("status_id[]", it) }
                    is AgeFilter -> filter.checked?.forEach { addQueryParameter("age_rating[]", it) }
                    is RatingFilter -> filter.checked?.forEach { addQueryParameter("rating[]", it) }
                    is YearFilter -> filter.checked?.forEach { addQueryParameter("year[]", it) }
                    is ChapterCountFilter -> filter.checked?.forEach { addQueryParameter("chapters[]", it) }
                    is SortFilter -> addQueryParameter("sort", filter.selected)
                    else -> {}
                }
            }

            if (filters == null) {
                addQueryParameter("sort", sortBy)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        client.get(url).use {
            return parseSearchManga(it.asJsoup())
        }
    }

    private fun parseSearchManga(document: Document): MangasPage {
        val mangas = document.select(".cards .cards__item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst(".cards__name")!!.text()

                val slug = "$baseUrl$url".toHttpUrl().pathSegments.last()
                thumbnail_url = "$baseUrl/img/manga/posters/$slug.jpg"
            }
        }

        val hasNextPage = document.selectFirst(".pagination .pagination__button a:contains(Вперёд)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "manga") {
            val tmpManga = SManga.create().apply {
                this.url = url.encodedPath
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga Details ======================================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val newUrl = manga.url
        val document = client.get("${baseUrl}$newUrl", ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw Exception("Контент не доступен.\nВозможно может помочь авторизация через WebView")
                } else {
                    throw HttpException(response.code)
                }
            }
            response.asJsoup()
        }
        val mangaNew = parseMangaDetails(document, newUrl)

        val chaptersNew = if (fetchChapters) {
            parseChapterList(document)
        } else {
            chapters
        }

        return SMangaUpdate(mangaNew, chaptersNew)
    }

    // ============================== Manga Utilities ===============================
    private fun parseMangaDetails(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        title = document.selectFirst("h1, .manga__name, .manga-mobile__name")!!.text()
        url = mangaUrl
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
                    append(String.format(Locale.forLanguageTag("ru"), "Рейтинг: %.0f%%", it * 100))
                }

            document // views
                .selectFirst(".manga__views")
                ?.text()
                ?.replace(" ", "")
                ?.toIntOrNull()
                ?.also {
                    if (isNotEmpty()) append("\n\n")
                    append(String.format(Locale.forLanguageTag("ru"), "Просмотров: %,d", it))
                }

            document // favorites
                .selectFirst(".manga")
                ?.attr("data-fav-count")
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?.also {
                    if (isNotEmpty()) append("\n\n")
                    append(String.format(Locale.forLanguageTag("ru"), "Избранное: %,d", it))
                }

            document // alternative names
                .select(".manga__name-alt > span, .manga-mobile__name-alt > span")
                .eachText()
                .takeIf { it.isNotEmpty() }
                ?.also {
                    if (isNotEmpty()) append("\n\n")
                    append("Альтернативные названия:\n")
                    append(it.joinToString("\n") { altName -> "• $altName" })
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

    // ============================== Chapters Utilities ===============================
    private suspend fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select("a.chapters__item").map(::chapterFromElement).toMutableList()

        // HTML only shows 100 entries. If this class is present it will load more via API
        if (document.selectFirst(".load-chapters-trigger") != null) {
            val mangaId = document.selectFirst(".manga")?.attr("data-id")
                ?: throw Exception("Не удалось найти ID манги")

            val form = FormBody.Builder()
                .add("manga_id", mangaId)
                .build()

            val moreChapters = client.post("$baseUrl/chapters/load", form)
                .parseAs<Dto>()
                .content
                .let { Jsoup.parseBodyFragment(it, baseUrl) }
                .select("a.chapters__item")
                .map(::chapterFromElement)

            chapters.addAll(moreChapters)
        }

        return chapters
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.select(".chapters__volume, .chapters__value, .chapters__name").text()
        date_upload = runCatching {
            LocalDate.parse(element.selectFirst(".chapters__add-date")?.text(), dateFormat).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrDefault(0L)
        chapter_number = element.select(".chapters__value").text()
            .substringAfter(" ").toFloatOrNull() ?: -1f
    }

    // ============================== Pages ======================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get("$baseUrl${chapter.url}").asJsoup()
        return data.select(".reader__pages img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    // ============================== Filters ======================================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val data = client.get("$baseUrl/manga/").asJsoup()

        return FiltersData(
            genres = data.select(".sl-select[name=\"genres[]\"] option").map { it.text() to it.attr("value") },
            tags = data.select(".sl-select[name=\"tags[]\"] option").map { it.text() to it.attr("value") },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val dto = data?.parseAs<FiltersData>()

        val filters = mutableListOf(
            Filter.Header("ПРИМЕЧАНИЕ: Игнорируется, если используется поиск по тексту!"),
            Filter.Separator(),
            SortFilter(),
        )

        if (dto?.genres?.isNotEmpty() == true) {
            filters.add(GenreFilter(dto.genres))
        }
        if (dto?.tags?.isNotEmpty() == true) {
            filters.add(TagFilter(dto.tags))
        }

        filters.addAll(
            listOf(
                TypeFilter(),
                StatusFilter(),
                AgeFilter(),
                RatingFilter(),
                YearFilter(),
                ChapterCountFilter(),
            ),
        )

        return FilterList(filters)
    }

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

    // ============================== Preferences ======================================
    private fun imgConvert(): Boolean = preferences.getBoolean(CONVERT_IMG_PREF, true)
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = CONVERT_IMG_PREF
            title = CONVERT_IMG_PREF_TITLE
            summary = CONVERT_IMG_PREF_SUM
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
        private val WEBP_MEDIA_TYPE = "image/webp".toMediaType()
        private const val CONVERT_IMG_PREF = "convert_img_pref"
        private const val CONVERT_IMG_PREF_TITLE = "Исправлять изображения"
        private const val CONVERT_IMG_PREF_SUM = "ⓘПриложение будет пытаться исправить изображения низкого качества.\nИзображения с анимацией будут конвертированы в статические при исправлении."
    }
}
