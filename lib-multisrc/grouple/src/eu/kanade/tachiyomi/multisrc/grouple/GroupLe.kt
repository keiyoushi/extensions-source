package eu.kanade.tachiyomi.multisrc.grouple

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

abstract class GroupLe(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
) : ParsedHttpSource(),
    ConfigurableSource {
    private val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.ROOT)

    private val preferences: SharedPreferences by getPreferencesLazy()

    protected open val isNeedAuth = false

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().rateLimit(2).addNetworkInterceptor { chain ->
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)
        if (originalRequest.url.toString().contains(baseUrl) && (
                originalRequest.url.toString()
                    .contains("internal/redirect") or (response.code == 301)
                )
        ) {
            if (originalRequest.url.toString().contains("/list?")) {
                throw IOException("Смените домен: Поисковик > Расширения > $name > ⚙\uFE0F")
            }
            throw IOException(
                "URL серии изменился. Перенесите/мигрируйте с $name на $name (или смежный с GroupLe), чтобы список глав обновился",
            )
        }
        response
    }.build()

    private val uagent = preferences.getString(UAGENT_TITLE, UAGENT_DEFAULT)!!

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", uagent)
        add("Referer", baseUrl)
    }

    override fun popularMangaSelector() = "div.tile"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/list?sortType=rate&offset=${50 * (page - 1)}", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list?sortType=updated&offset=${50 * (page - 1)}", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("img.lazy")?.attr("data-original")?.replace("_p.", ".")
        element.selectFirst("h3 > a")?.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.nextLink"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/advancedResults?offset=${50 * (page - 1)}".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(genre.id, arrayOf("=", "=in", "=ex")[genre.state])
                    }
                }

                is CategoryList -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(category.id, arrayOf("=", "=in", "=ex")[category.state])
                    }
                }

                is AgeList -> filter.state.forEach { age ->
                    if (age.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(age.id, arrayOf("=", "=in", "=ex")[age.state])
                    }
                }

                is MoreList -> filter.state.forEach { more ->
                    if (more.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(more.id, arrayOf("=", "=in", "=ex")[more.state])
                    }
                }

                is AdditionalFilterList -> filter.state.forEach { fils ->
                    if (fils.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(fils.id, arrayOf("=", "=in", "=ex")[fils.state])
                    }
                }

                is OrderBy -> {
                    url.addQueryParameter(
                        "sortType",
                        arrayOf("RATING", "POPULARITY", "YEAR", "NAME", "DATE_CREATE", "DATE_UPDATE", "USER_RATING")[filter.state],
                    )
                }

                else -> {}
            }
        }

        return GET(url.toString().replace("=%3D", "="), headers)
    }

    protected class OrderBy :
        Filter.Select<String>(
            "Сортировка",
            arrayOf("По популярности", "Популярно сейчас", "По году", "По алфавиту", "Новинки", "По дате обновления", "По рейтингу"),
        )

    protected class Genre(name: String, val id: String) : Filter.TriState(name)

    protected class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)
    protected class CategoryList(categories: List<Genre>) : Filter.Group<Genre>("Категории", categories)
    protected class AgeList(ages: List<Genre>) : Filter.Group<Genre>("Возрастная рекомендация", ages)
    protected class MoreList(more: List<Genre>) : Filter.Group<Genre>("Прочее", more)
    protected class AdditionalFilterList(fils: List<Genre>) : Filter.Group<Genre>("Фильтры", fils)

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title =
            document.selectFirst(".cr-hero-names__main")?.text() ?: document.selectFirst("meta[itemprop=name]")?.attr("content").orEmpty()

        val details = mutableMapOf<String, String>()
        document.selectFirst(".cr-hero .cr-info-details")?.children()?.forEach { element ->
            val title = element.selectFirst(".cr-info-details-item__title")?.text()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val value = element.selectFirst(".cr-info-details-item__status")?.text()?.trim()?.lowercase(Locale.ROOT).orEmpty()

            if (title.isNotEmpty() && value.isNotEmpty() && !details.containsKey(title)) {
                details[title] = value
            }
        }

        val releaseStatus = details["выпуск"] ?: ""
        val translationStatus = details["перевод"] ?: ""

        manga.status = when {
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
            val role = person.selectFirst(".cr-main-person-item__role")?.text()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val name = person.selectFirst(".cr-main-person-item__name a, .cr-main-person-item__name")?.text()?.trim()

            if (name.isNullOrBlank()) return@forEach
            when {
                role.contains("автор") || role.contains("сценар") -> authorNames += name
                role.contains("худож") || role.contains("иллюст") -> artistNames += name
            }
        }
        manga.author = authorNames.distinct().joinToString(", ").takeIf { it.isNotBlank() }
        manga.artist = artistNames.distinct().joinToString(", ").takeIf { it.isNotBlank() }

        val category = document.selectFirst(".cr-hero-short-details a[href*=\"/list/category/\"]")?.text().orEmpty()
        val age = normalizeAgeRating(
            document.selectFirst(".cr-hero-short-details a[href*=\"/list/limitation/\"]")?.text().orEmpty(),
        )
        val tags = document.select(".cr-tags .cr-tags__item").mapNotNull { tag ->
            tag.select("span").last()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        }

        manga.genre =
            listOf(category, age).asSequence().plus(tags).map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()
                .joinToString(", ")

        val altNames =
            document.select("#alt-names-dialog .modal-body .py-1").mapNotNull { it.text().trim().takeIf(String::isNotBlank) }.distinct()
                .takeIf { it.isNotEmpty() }?.let { "Альтернативные названия:\n${it.joinToString(" / ")}\n\n" } ?: ""

        val ratingValue = document.selectFirst(".cr-hero-rating .cr-hero-rating__value")?.text()?.toFloatOrNull()

        val ratingSummary = ratingValue?.let { rating ->
            val ratingVotes = document.selectFirst(".cr-hero-rating__text")?.text()?.filter { it.isDigit() } ?: "0"

            "${ratingToStars(rating)} $rating (голосов: $ratingVotes)\n"
        } ?: ""

        val descriptionText = document.selectFirst(".cr-description__content")?.text().orEmpty()

        manga.description = ratingSummary + altNames + descriptionText

        val thumbElement = document.selectFirst(".cr-hero-poster__img") ?: document.selectFirst(".cr-hero-overlay__bg")
        manga.thumbnail_url = thumbElement?.let { element ->
            element.attr("src").ifEmpty { element.attr("data-src") }.ifEmpty { element.attr("data-original") }
                .ifEmpty { element.attr("data-bg") }
        }.orEmpty()

        return manga
    }

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

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = if (manga.status != SManga.LICENSED) {
        client.newCall(chapterListRequest(manga)).asObservableSuccess().map { response -> chapterListParse(response, manga) }
    } else {
        Observable.error(java.lang.Exception("Лицензировано - Нет глав"))
    }

    protected open fun getChapterSearchParams(document: Document): String {
        val scriptContent = document.selectFirst("script:containsData(user_hash)")?.data()
        val userHash = scriptContent?.let { USER_HASH_REGEX.find(it)?.groupValues?.get(1) }
        return userHash?.let { "?d=$it&mtr=true" } ?: "?mtr=true"
    }

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()

        authGuard(document)

        val chapterSearchParams = getChapterSearchParams(document)

        return document.select(chapterListSelector()).map { chapterFromElement(it, manga, chapterSearchParams) }
    }

    override fun chapterListSelector() = "tr.item-row:has(td > a):has(td.date:not(.text-info))"

    private fun chapterFromElement(element: Element, manga: SManga, chapterSearchParams: String): SChapter {
        val urlElement = element.selectFirst("a.chapter-link")!!
        val chapterInf = element.selectFirst("td.item-title")!!
        val urlText = urlElement.text()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href") + chapterSearchParams)

        chapter.scanlator = chapterScanlatorFromElement(urlElement, element)

        chapter.name = urlText.removeSuffix(" новое").trim()
        if (manga.title.length > 25) {
            for (word in manga.title.split(' ')) {
                chapter.name = chapter.name.removePrefix(word).trim()
            }
        }
        val dots = chapter.name.indexOf("…")
        val numbers = chapter.name.findAnyOf(IntRange(0, 9).map { it.toString() })?.first ?: 0

        if (dots in 0 until numbers) {
            chapter.name = chapter.name.substringAfter("…").trim()
        }

        chapter.chapter_number = chapterInf.attr("data-num").toFloat() / 10

        chapter.date_upload = dateFormat.tryParse(element.select("td.d-none").last()?.text())
        return chapter
    }

    protected open fun chapterScanlatorFromElement(chapterLinkElement: Element, chapterRowElement: Element): String {
        val translatorElement = chapterLinkElement.attr("title")
        return translatorElement.takeIf { it.isNotBlank() }?.replace("(Переводчик),", "&")?.removeSuffix(" (Переводчик)") ?: ""
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val extra = Regex("""\s*([0-9]+\sЭкстра)\s*""")
        val single = Regex("""\s*Сингл\s*""")
        when {
            extra.containsMatchIn(chapter.name) -> {
                if (chapter.name.substringAfter("Экстра").trim().isEmpty()) {
                    chapter.name = chapter.name.replaceFirst(
                        " ",
                        " - " + DecimalFormat("#,###.##").format(chapter.chapter_number).replace(",", ".") + " ",
                    )
                }
            }

            single.containsMatchIn(chapter.name) -> {
                if (chapter.name.substringAfter("Сингл").trim().isEmpty()) {
                    chapter.name = DecimalFormat("#,###.##").format(chapter.chapter_number).replace(",", ".") + " " + chapter.name
                }
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        authGuard(document)

        val html = document.html()

        val readerMark = when {
            html.contains("rm_h.readerInit(") -> "rm_h.readerInit("

            html.contains("rm_h.readerDoInit(") -> "rm_h.readerDoInit("

            !response.request.url.toString().contains(baseUrl) -> {
                throw Exception("Не удалось загрузить главу. Url: ${response.request.url}")
            }

            else -> {
                if (document.selectFirst("div.alert") != null || document.selectFirst("form.purchase-form") != null) {
                    throw Exception("Эта глава платная. Используйте сайт, чтобы купить и прочитать ее.")
                }
                throw Exception("Дизайн сайта обновлен, для дальнейшей работы необходимо обновление дополнения")
            }
        }

        val beginIndex = html.indexOf(readerMark)
        val endIndex = html.indexOf(");", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)

        val p = Pattern.compile("'.*?','.*?',\".*?\"")
        val m = p.matcher(trimmedHtml)

        val pages = mutableListOf<Page>()

        var i = 0
        while (m.find()) {
            val urlParts = m.group().replace("[\"\']+".toRegex(), "").split(',')
            var url = if (urlParts[1].isEmpty() && urlParts[2].startsWith("/static/")) {
                baseUrl + urlParts[2]
            } else {
                if (urlParts[1].endsWith("/manga/")) {
                    urlParts[0] + urlParts[2]
                } else {
                    urlParts[1] + urlParts[0] + urlParts[2]
                }
            }
            if (!url.contains("://")) {
                url = "https:$url"
            }
            if (url.contains("one-way.work")) {
                // domain that does not need a token
                url = url.substringBefore("?")
            }
            pages.add(Page(i++, "", url.replace("//resh", "//h")))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            val titleId = url.pathSegments.firstOrNull()?.takeIf { it.isNotEmpty() } ?: throw Exception("Unsupported url")
            return fetchSearchManga(page, "$PREFIX_SLUG_SEARCH$titleId", filters)
        }

        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)
            client.newCall(GET("$baseUrl/$realQuery", headers)).asObservableSuccess().map { response ->
                val details = mangaDetailsParse(response)
                details.url = "/$realQuery"
                MangasPage(listOf(details), false)
            }
        } else {
            client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess().map(::searchMangaParse)
        }
    }

    private fun authGuard(document: Document) {
        if (document.select(".user-avatar").isEmpty() && isNeedAuth) {
            throw Exception("Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E")
        }
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = UAGENT_TITLE
            title = UAGENT_TITLE
            summary = uagent
            setDefaultValue(UAGENT_DEFAULT)
            dialogTitle = UAGENT_TITLE
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(UAGENT_TITLE, newValue as String).commit()
                    Toast.makeText(
                        screen.context,
                        "Для смены User-Agent необходимо перезапустить приложение с полной остановкой.",
                        Toast.LENGTH_LONG,
                    ).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val UAGENT_TITLE = "User-Agent(для некоторых стран)"
        private const val UAGENT_DEFAULT = "arora"
        const val PREFIX_SLUG_SEARCH = "slug:"
        private val USER_HASH_REGEX = "user_hash.+'(.+)'".toRegex()
    }
}
