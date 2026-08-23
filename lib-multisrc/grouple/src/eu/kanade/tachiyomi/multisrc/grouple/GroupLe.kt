package eu.kanade.tachiyomi.multisrc.grouple

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class GroupLe :
    KeiSource(),
    ConfigurableSource {
    private val preferences: SharedPreferences by getPreferencesLazy()

    protected open val siteId: Int get() = 0
    protected open val authApiUrl: String = "https://3.grouple.co"
    private val authApiHttpUrl by lazy { authApiUrl.toHttpUrl() }

    private val apiHeaders get() = headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "cross-site")
        .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addNetworkInterceptor { chain ->
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)
            if (originalRequest.url.toString().contains(baseUrl) && (
                    originalRequest.url.toString().contains("internal/redirect") || response.code == 301
                    )
            ) {
                if (originalRequest.url.toString().contains("api/catalog")) {
                    throw IOException("Смените домен: Поисковик > Расширения > $name > ⚙\uFE0F")
                }
                throw IOException(
                    "URL серии изменился. Перенесите/мигрируйте с $name на $name (или смежный с GroupLe), чтобы список глав обновился",
                )
            }
            response
        }
        addInterceptor(::authInterceptor)
        rateLimit(2)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        val userAgent = preferences.getString(UAGENT_TITLE, UAGENT_DEFAULT) ?: UAGENT_DEFAULT
        set("User-Agent", userAgent)
    }

    // ============================== Interceptors ===============================
    protected open fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Skip image requests (by file extension or Accept header)
        val extension = request.url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', "")
            ?.lowercase().orEmpty()
        if (extension in IMAGE_EXTENSIONS || request.header("Accept")?.contains("image") == true) {
            return chain.proceed(request)
        }

        val path = request.url.encodedPath
        // Avoid loops while login
        if (path.contains("/internal/auth") || path.contains("/login/")) {
            return chain.proceed(request)
        }

        // This sites will fail auth. After login usagi.one redirects to ReadManga url, x.ahen.me to 20.allhen.online
        // Host itself won't authorize on them. Any attempt to access this sites after authorization will open site without authorization.
        // Code will throw Exception error since end url are not baseUrl.
        if (request.url.host.contains("usagi.one") || request.url.host.contains("ahen.me")) {
            return chain.proceed(request)
        }

        val cookieStore = client.cookieJar
        val baseHttpUrl = baseUrl.toHttpUrl()
        val hasRememberMe = cookieStore.loadForRequest(baseHttpUrl).any { it.name == "remember_me" }

        // Cookie exists, no need to proceed
        if (hasRememberMe) return chain.proceed(request)

        val checkAuth = autoAuth()
        val hasRememberMeAuth = cookieStore.loadForRequest(authApiHttpUrl).any { it.name == "remember_me" }

        if (hasRememberMeAuth || checkAuth) {
            // Request to trigger refresh of cookies before new auth (auth server usually holds cookie longer than main site)
            val authRequest = request.newBuilder()
                .url("$baseUrl/internal/auth")
                .get()
                .build()

            val authResponse = chain.proceed(authRequest)
            val finalUrl = authResponse.request.url
            authResponse.close()

            if (checkAuth && finalUrl.host == authApiHttpUrl.host) {
                val authLogin = getAuthLogin()
                val authPass = getAuthPass()
                if (authLogin.isNotBlank() && authPass.isNotBlank()) {
                    val timestamp = System.currentTimeMillis()
                    val loginUrl = "$authApiUrl/login/authenticate?ttt=$timestamp&siteId=$siteId"

                    val verifyBody = FormBody.Builder()
                        .add("targetUri", "/login/continueSso?siteId=$siteId&targetUri=")
                        .add("username", authLogin)
                        .add("password", authPass)
                        .add("remember_me", "true")
                        .add("_remember_me_yes", "")
                        .add("remember_me_yes", "on")
                        .build()

                    val loginRequest = request.newBuilder()
                        .url(loginUrl)
                        .headers(headers)
                        .post(verifyBody)
                        .build()

                    val loginResponse = chain.proceed(loginRequest)
                    val loginFinalHttpUrl = loginResponse.request.url
                    loginResponse.close()

                    if (loginFinalHttpUrl.host == authApiHttpUrl.host || loginFinalHttpUrl.queryParameter("login_error") == "1") {
                        preferences.edit().putBoolean(AUTO_AUTH, false).apply()
                        throw IOException("Ошибка авторизации. Автоматическая авторизация будет отключена.")
                    }
                }
            }
        }

        return chain.proceed(request)
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeSearchRequest("RATING", page)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeSearchRequest("DATE_UPDATE", page)

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeSearchRequest("RATING", page, query, filters)

    // ============================== Search Utilities ===============================
    protected open suspend fun makeSearchRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = "$baseUrl/api/catalog/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("offset", (50 * (page - 1)).toString())
            query?.takeIf { it.isNotBlank() }?.let { addQueryParameter("q", query) }

            filters?.forEach { filter ->
                when (filter) {
                    is OrderBy -> addQueryParameter("sortType", filter.selected)
                    is StatusFilter -> {
                        filter.included?.forEach { addQueryParameter("includeProductionStatuses", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeProductionStatuses", it) }
                    }
                    is TranslationStatusFilter -> {
                        filter.included?.forEach { addQueryParameter("includeTranslationStatuses", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeTranslationStatuses", it) }
                    }
                    is GenreFilter -> {
                        filter.included?.forEach { addQueryParameter("includeElementIds", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeElementIds", it) }
                    }
                    is TagsFilter -> {
                        filter.included?.forEach { addQueryParameter("includeElementIds", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeElementIds", it) }
                    }
                    is CategoryFilter -> {
                        filter.included?.forEach { addQueryParameter("includeElementIds", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeElementIds", it) }
                    }
                    is LimitationFilter -> {
                        filter.included?.forEach { addQueryParameter("includeElementIds", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeElementIds", it) }
                    }
                    is AnotherFilter -> {
                        filter.included?.forEach { addQueryParameter("includeElementIds", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeElementIds", it) }
                    }
                    is AdditionalFilters -> {
                        filter.included?.forEach { addQueryParameter("includeSearchFilter", it) }
                        filter.excluded?.forEach { addQueryParameter("excludeSearchFilter", it) }
                    }
                    is YearRangeFilter -> {
                        val min = checkMinRange(filter.minValue, filter.minYear, filter.maxYear)
                        val max = checkMaxRange(filter.maxValue, filter.minYear, filter.maxYear)
                        addQueryParameter("years", "$min,$max")
                    }
                    else -> {}
                }
            }

            if (filters == null) {
                addQueryParameter("sortType", sortBy)
            }
        }.build()

        val result = client.get(url, apiHeaders).parseAs<SearchResponse>()
        val mangas = result.list.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage)
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0].isNotBlank()) {
            val tmpManga = SManga.create().apply {
                this.url = "/${url.pathSegments[0]}"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga ======================================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val newUrl = manga.url
        val document = client.get("${baseUrl}$newUrl").asJsoup()

        val mangaNew = parseMangaDetails(document, newUrl)
        val chaptersNew = if (fetchChapters) {
            authGuard(document)
            parseChapterList(document, mangaNew)
        } else {
            chapters
        }

        return SMangaUpdate(mangaNew, chaptersNew)
    }

    // ============================== Manga Details ===============================
    protected open val tagsSelector: String = ".creation-element-tags .creation-element-tags__item:not(.creation-element-tags__item--misc) span:not(.text-secondary)"
    protected open fun parseMangaDetails(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = document.selectFirst(".cr-hero-names__main")?.text()
            ?: document.selectFirst("meta[itemprop=name]")?.attr("content")!!

        val details = mutableMapOf<String, String>()
        document.selectFirst(".cr-hero .cr-info-details")?.children()?.forEach { element ->
            val title = element.selectFirst(".cr-info-details-item__title")?.text()?.lowercase(Locale.ROOT).orEmpty()
            val value = element.selectFirst(".cr-info-details-item__status")?.text()?.lowercase(Locale.ROOT).orEmpty()

            if (title.isNotEmpty() && value.isNotEmpty() && !details.containsKey(title)) {
                details[title] = value
            }
        }

        val releaseStatus = details["выпуск"] ?: ""
        val translationStatus = details["перевод"] ?: ""

        status = when {
            releaseStatus.contains("продолж") || releaseStatus.contains("начат") -> SManga.ONGOING

            releaseStatus.contains("заверш") -> if (translationStatus.contains("заверш")) {
                SManga.COMPLETED
            } else {
                SManga.PUBLISHING_FINISHED
            }
            releaseStatus.contains("приост") || releaseStatus.contains("заморож") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        val authorNames = mutableListOf<String>()
        val artistNames = mutableListOf<String>()
        document.select(".cr-main-person-item").forEach { person ->
            val role = person.selectFirst(".cr-main-person-item__role")?.text()?.lowercase(Locale.ROOT).orEmpty()
            val name = person.select(".cr-main-person-item__name a, .cr-main-person-item__name").map { it.text() }

            if (name.isEmpty()) return@forEach
            when {
                role.contains("автор") || role.contains("сценар") -> authorNames += name
                role.contains("худож") || role.contains("иллюст") -> artistNames += name
            }
        }
        author = authorNames.distinct().joinToString().takeIf { it.isNotBlank() }
        artist = artistNames.distinct().joinToString().takeIf { it.isNotBlank() }

        val category = document.selectFirst(".cr-hero-short-details a[href*=\"/list/category/\"]")?.text().orEmpty()
        val age = normalizeAgeRating(
            document.selectFirst(".cr-hero-short-details a[href*=\"/list/limitation/\"]")?.text().orEmpty(),
        )
        val tags = document
            .select(tagsSelector)
            .map { it.text() }

        genre = buildList {
            add(category)
            add(age)
            addAll(tags)
        }.filter { it.isNotBlank() }.map { it.lowercase(Locale.ROOT) }.distinct().joinToString()

        val altNames = document.select("#alt-names-dialog .modal-body .py-1")
            .mapNotNull { it.text().takeIf(String::isNotEmpty) }
            .distinct()
            .takeIf { it.isNotEmpty() }

        val ratingValue = document.selectFirst(".cr-hero-rating .cr-hero-rating__value")?.text()?.toFloatOrNull()

        val ratingSummary = ratingValue?.let { rating ->
            val ratingVotes = document.selectFirst(".cr-hero-rating__text")?.text()?.filter { it.isDigit() } ?: "0"

            "${ratingToStars(rating)} $rating (голосов: $ratingVotes)"
        }

        val descriptionText = document.selectFirst(".cr-description__content")?.text()

        description = buildString {
            ratingSummary?.let { append(it) }
            descriptionText?.let {
                if (isNotBlank()) append("\n")
                append(it)
            }
            altNames?.let {
                if (isNotBlank()) append("\n")
                append("**Альтернативные названия**:\n")
                append(altNames.joinToString("\n") { "- $it" })
            }
        }

        val thumbElement = document.selectFirst(".cr-hero-poster__img") ?: document.selectFirst(".cr-hero-overlay__bg")
        thumbnail_url = thumbElement?.let { element ->
            element.absUrl("src").ifEmpty { element.absUrl("data-src") }
                .ifEmpty { element.absUrl("data-original") }
                .ifEmpty { element.absUrl("data-bg") }
        }.orEmpty()
    }

    // ============================== Manga Utilities ===============================
    protected fun normalizeAgeRating(rawAgeValue: String): String = when (rawAgeValue) {
        "NC-17", "R18+" -> "18+"
        "R", "G", "PG" -> "16+"
        "PG-13" -> "12+"
        else -> rawAgeValue
    }

    protected fun ratingToStars(ratingValue: Float): String = when {
        ratingValue > 9.5f -> "★★★★★"
        ratingValue > 8.5f -> "★★★★✬"
        ratingValue > 7.5f -> "★★★★☆"
        ratingValue > 6.5f -> "★★★✬☆"
        ratingValue > 5.5f -> "★★★☆☆"
        ratingValue > 4.5f -> "★★✬☆☆"
        ratingValue > 3.5f -> "★★☆☆☆"
        ratingValue > 2.5f -> "★✬☆☆☆"
        ratingValue > 1.5f -> "★☆☆☆☆"
        ratingValue > 0.5f -> "✬☆☆☆☆"
        else -> "☆☆☆☆☆"
    }

    // ============================== Chapters Details ===============================
    protected open fun parseChapterList(document: Document, manga: SManga): List<SChapter> {
        val chapterSearchParams = getChapterSearchParams(document)

        if (document.selectFirst(".alert.alert-warning:contains(Запрещена публикация произведения по копирайту)") != null) {
            throw Exception("Лицензировано - Главы удалены по требованию правообладателя.")
        }

        return document.select("tr.item-row:has(td > a):has(td.date:not(.text-info))").map { element ->
            SChapter.create().apply {
                val urlElement = element.selectFirst("a.chapter-link")!!
                val chapterInf = element.selectFirst("td.item-title")!!

                setUrlWithoutDomain(urlElement.absUrl("href") + chapterSearchParams)

                scanlator = chapterScanlatorFromElement(urlElement, element)

                chapter_number = chapterInf.attr("data-num").toFloat() / 10

                name = urlElement.ownText().trim()
                if (manga.title.length > 25) {
                    for (word in manga.title.split(' ')) {
                        name = name.removePrefix(word).trim()
                    }
                }

                val dots = name.indexOf("…")
                val numbers = name.findAnyOf(IntRange(0, 9).map { it.toString() })?.first ?: 0

                if (dots in 0 until numbers) {
                    name = name.substringAfter("…").trim()
                }

                when {
                    EXTRA_REGEX.containsMatchIn(name) -> {
                        if (name.substringAfter("Экстра").isBlank()) {
                            name = name.replaceFirst(
                                " ",
                                " - " + DecimalFormat("#,###.##").format(chapter_number).replace(",", ".") + " ",
                            )
                        }
                    }

                    SINGLE_REGEX.containsMatchIn(name) -> {
                        if (name.substringAfter("Сингл").isBlank()) {
                            name = DecimalFormat("#,###.##").format(chapter_number).replace(",", ".") + " " + name
                        }
                    }
                }

                date_upload = dateFormat.tryParseDate(element.select("td.d-none").last()?.text())
            }
        }
    }

    protected open fun getChapterSearchParams(document: Document): String {
        val scriptContent = document.selectFirst("script:containsData(user_hash)")?.data()
        val userHash = scriptContent?.let { USER_HASH_REGEX.find(it)?.groupValues?.get(1) }
        return userHash?.let {
            val prefHash = getUserHash()
            if (prefHash != it) {
                preferences.edit().putString(USER_HASH_PREF, it).apply()
            }
            "?d=$it&mtr=true"
        } ?: "?mtr=true"
    }

    protected open fun chapterScanlatorFromElement(chapterLinkElement: Element, chapterRowElement: Element): String {
        val translatorElement = chapterLinkElement.attr("title")
        return translatorElement.takeIf { it.isNotBlank() }
            ?.replace("(Переводчик),", "&")
            ?.replace("Переводчик,", "&")
            ?.removeSuffix(" (Переводчик)")
            ?.removeSuffix(" Переводчик")
            ?: ""
    }

    // ============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // User hash are now mandatory for chapters that require authorization. If it's not passed, site will replace images with default image
        // For example: https://a.zazaza.me/podniatie_urovnia_v_odinochku__A5ea4/vol3/200
        // User hash are permanent and can be saved in the preferences, to add it in all URL's if it's somehow missed (in old saved entries, or user got chapters list without authorization).
        // Doing it here to preserve old chapter.url values
        val url = "$baseUrl${chapter.url}".toHttpUrl().let { httpUrl ->
            val userHash = getUserHash()
            if (userHash != "none" && !httpUrl.queryParameterNames.contains("d")) {
                httpUrl.newBuilder().setQueryParameter("d", userHash).build()
            } else {
                httpUrl
            }
        }
        val document = client.get(url, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw Exception("Для просмотра главы необходима авторизация через WebView\uD83C\uDF0E (Ошибка 404)  или включите автоматическую авторизацию в настройках расширения")
                } else {
                    throw HttpException(response.code)
                }
            }
            if (!response.request.url.toString().contains(baseUrl)) throw Exception("Не удалось загрузить главу. Url: ${response.request.url}")
            response.asJsoup()
        }

        if (document.selectFirst("div.alert") != null || document.selectFirst("form.purchase-form") != null) {
            throw Exception("Эта глава платная. Используйте сайт, чтобы купить и прочитать ее.")
        }

        if (document.selectFirst("h1:contains(требуется премиум)") != null) { // MintManga
            throw Exception("Для доступа к главе требуется премиум-подписка.")
        }

        val html = document.selectFirst("script:containsData(chapterInfo)")?.data()

        val readerMark = when {
            html?.contains("rm_h.readerInit(") == true -> "rm_h.readerInit("
            html?.contains("rm_h.readerDoInit(") == true -> "rm_h.readerDoInit("
            else -> throw Exception("Дизайн сайта обновлен, для дальнейшей работы необходимо обновление дополнения")
        }

        val beginIndex = html.indexOf(readerMark)
        val endIndex = html.indexOf(");", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)
        val pages = mutableListOf<Page>()

        PAGES_REGEX.findAll(trimmedHtml).forEachIndexed { idx, result ->
            val host = result.groupValues[1]
            val middle = result.groupValues[2]
            val end = result.groupValues[3]

            var imageUrl = if (middle.isBlank() && end.startsWith("/static/")) {
                baseUrl + end
            } else {
                if (middle.endsWith("/manga/")) {
                    host + end
                } else {
                    middle + host + end
                }
            }
            if (!imageUrl.contains("://")) {
                imageUrl = "https:$imageUrl"
            }
            if (imageUrl.contains("one-way.work")) {
                // domain that does not need a token
                imageUrl = imageUrl.substringBefore("?")
            }
            pages.add(Page(idx, imageUrl = imageUrl.replace("//resh", "//h")))
        }

        return pages
    }

    // =========================== Related Manga (Komikku) ============================
    override val supportsRelatedMangas: Boolean = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = "$baseUrl/list/like${manga.url.substringBefore("__")}"
        val document = client.get(url).asJsoup()
        return document.select(".entity-cards-grid .similar-item-card").mapNotNull {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.entity-card-tile")?.absUrl("href") ?: return@mapNotNull null)
                title = it.selectFirst(".entity-card-tile__title")?.text() ?: return@mapNotNull null
                thumbnail_url = it.selectFirst("img.ui-cover")?.attr("data-src") ?: ""
            }
        }
    }

    // ============================== Filters ===============================
    protected open val defaultSortOrder = "RATING"
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        // Так же доступны в API /api/catalog/elementsByType?type=
        // Для каждого типа фильтров свой запрос, например жанры: /api/catalog/elementsByType?type=2
        val resultDeferred = async { client.get("$baseUrl/search/advanced").asJsoup() }
        val result2Deferred = async { client.get("$baseUrl/api/catalog/elementsByType?type=40").parseAs<FiltersAPIResponse>() }

        val result = resultDeferred.await()
        val result2 = result2Deferred.await()

        val data = result.selectFirst("script:containsData(window.__FILTERS)")?.data()
            ?: throw Exception("Не удалось найти данные о фильтрах")

        val f = mutableMapOf<String, String>()

        FILTERS_REGEX.findAll(data).forEach { filter ->
            f += filter.groupValues[1] to filter.groupValues[2]
        }

        FiltersData(
            sortType = f["sortType"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            productionStatus = f["productionStatus"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            translationStatus = f["translationStatus"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            searchFilters = f["searchFilters"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            genre = f["genre"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            category = f["category"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            limitation = f["limitation"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            another = f["another"]?.parseAs<Map<String, String>>()?.map { it.value to it.key },
            tags = result2.results?.map { it.text to it.id },
            years = f["years"]?.let {
                // Исправляем JSON: { min: 1950, max: 2027 }.
                it.replace(CHECK_JSON, "\"$1\":").parseAs<YearsData>()
            },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        data?.parseAs<FiltersData>()?.let {
            if (it.sortType?.isNotEmpty() == true) filters.add(OrderBy(it.sortType, defaultSortOrder))
            if (it.genre?.isNotEmpty() == true) filters.add(GenreFilter(it.genre))
            if (it.tags?.isNotEmpty() == true) filters.add(TagsFilter(it.tags))
            if (it.category?.isNotEmpty() == true) filters.add(CategoryFilter(it.category))
            if (it.productionStatus?.isNotEmpty() == true) filters.add(StatusFilter(it.productionStatus))
            if (it.translationStatus?.isNotEmpty() == true) filters.add(TranslationStatusFilter(it.translationStatus))
            if (it.limitation?.isNotEmpty() == true) filters.add(LimitationFilter(it.limitation))
            if (it.another?.isNotEmpty() == true) filters.add(AnotherFilter(it.another))
            if (it.searchFilters?.isNotEmpty() == true) filters.add(AdditionalFilters(it.searchFilters))
            if (it.years != null) filters.add(YearRangeFilter(it.years))
        }
        return FilterList(filters)
    }

    // ============================== Utilities ===============================
    // Old authGuard() doesn't work properly now. Previously it checked only certain sites, but now any manga on any site can require authorization
    // Example: https://a.zazaza.me/podniatie_urovnia_v_odinochku__A5ea4
    // Now all HTML elements related to chapters have two classes: blockedForAnonymous, blockedNonB
    // When needed site adds JavaScript to the HTML that applies hidden status to these classes in CSS. authGuard now checks that this JS code are present in the HTML body
    // Check should be universal. AllHentai uses same classes, but different JS code, so it requires its own override/check
    protected open fun authGuard(document: Document) {
        document.select("script:containsData(UI.ViewHide)").joinToString().let {
            if (it.contains("\"name\":\"blockedForAnonymous\"") && document.selectFirst(".user-avatar") == null) {
                throw Exception("Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E или включите автоматическую авторизацию в настройках расширения")
            }
        }
    }
    private fun checkMinRange(input: String?, min: Int, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return min.toString()
        if (value !in min..max) return min.toString()
        return value.toString()
    }
    private fun checkMaxRange(input: String?, min: Int, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return max.toString()
        if (value !in min..max) return max.toString()
        return value.toString()
    }

    // ============================== Preferences ===============================
    private fun getUserHash(): String = preferences.getString(USER_HASH_PREF, "none") ?: "none"
    private fun autoAuth(): Boolean = preferences.getBoolean(AUTO_AUTH, false)
    private fun getAuthLogin(): String = preferences.getString(AUTO_AUTH_LOGIN, "") ?: ""
    private fun getAuthPass(): String = preferences.getString(AUTO_AUTH_PASS, "") ?: ""
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = UAGENT_TITLE
            title = UAGENT_TITLE
            summary = preferences.getString(UAGENT_TITLE, UAGENT_DEFAULT) ?: UAGENT_DEFAULT
            setDefaultValue(UAGENT_DEFAULT)
            dialogTitle = UAGENT_TITLE
            setOnPreferenceChangeListener { _, value ->
                Toast.makeText(
                    screen.context,
                    "Для смены User-Agent необходимо перезапустить приложение с полной остановкой.",
                    Toast.LENGTH_LONG,
                ).show()
                this.summary = value as CharSequence?
                true
            }
        }.let(screen::addPreference)

        val authLoginPref = EditTextPreference(screen.context).apply {
            key = AUTO_AUTH_LOGIN
            title = AUTO_AUTH_LOGIN_TITLE
            setDefaultValue("")
            summary = getAuthLogin()
            setOnPreferenceChangeListener { preference, newValue ->
                preference.summary = (newValue as String)
                true
            }
        }

        val authPasswordPref = EditTextPreference(screen.context).apply {
            key = AUTO_AUTH_PASS
            title = AUTO_AUTH_PASS_TITLE
            setDefaultValue("")
            summary = getAuthPass()
            setOnPreferenceChangeListener { preference, newValue ->
                preference.summary = (newValue as String)
                true
            }
        }

        val authAutoPref = SwitchPreferenceCompat(screen.context).apply {
            key = AUTO_AUTH
            title = AUTO_AUTH_TITLE
            summary = AUTO_AUTH_SUM
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (baseUrl.contains("usagi.one")) {
                    Toast.makeText(
                        screen.context,
                        "Автоматическая авторизация невозможна для $name. Мигрируйте на ReadManga.",
                        Toast.LENGTH_LONG,
                    ).show()
                    preferences.edit().putBoolean(AUTO_AUTH, false).apply()
                    this.setEnabled(false)
                    return@setOnPreferenceChangeListener false
                }
                if (baseUrl.contains("x.ahen.me")) {
                    Toast.makeText(
                        screen.context,
                        "Автоматическая авторизация невозможна на x.ahen.me. Используйте основной домен.",
                        Toast.LENGTH_LONG,
                    ).show()
                    preferences.edit().putBoolean(AUTO_AUTH, false).apply()
                    this.setEnabled(false)
                    return@setOnPreferenceChangeListener false
                }
                authLoginPref.setEnabled(enabled)
                authPasswordPref.setEnabled(enabled)
                true
            }
        }

        val isAutoAuthEnabled = preferences.getBoolean(AUTO_AUTH, false)
        authLoginPref.setEnabled(isAutoAuthEnabled)
        authPasswordPref.setEnabled(isAutoAuthEnabled)

        screen.addPreference(authAutoPref)
        screen.addPreference(authLoginPref)
        screen.addPreference(authPasswordPref)
    }

    companion object {
        private const val UAGENT_TITLE = "User-Agent(для некоторых стран)"
        private const val UAGENT_DEFAULT = "arora"
        private const val USER_HASH_PREF = "user_hash"
        private const val AUTO_AUTH = "auto_authorization"
        private const val AUTO_AUTH_TITLE = "Автоматическая авторизация"
        private const val AUTO_AUTH_SUM = "Расширение попытается авторизоваться с указанным логином и паролем"
        private const val AUTO_AUTH_LOGIN = "auto_authorization_login"
        private const val AUTO_AUTH_LOGIN_TITLE = "Логин для авторизации"
        private const val AUTO_AUTH_PASS = "auto_authorization_password"
        private const val AUTO_AUTH_PASS_TITLE = "Пароль для авторизации"
        private val USER_HASH_REGEX = "user_hash.+'(.+)'".toRegex()
        private val EXTRA_REGEX = Regex("""\s*([0-9]+\sЭкстра)\s*""")
        private val SINGLE_REGEX = Regex("""\s*Сингл\s*""")
        private val FILTERS_REGEX = """window\.__FILTERS\.(\w+)\s*=\s*([{].*?[}]);""".toRegex()
        private val PAGES_REGEX = """\[['"](.*?)['"],['"](.*?)['"],['"](.*?)['"].*?]""".toRegex()
        private val CHECK_JSON = """(\w+)\s*:""".toRegex()
        private val dateFormat = DateTimeFormatter.ofPattern("[dd.MM.yy][d.MM.yy]", Locale.ROOT)
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "svg")
    }
}
