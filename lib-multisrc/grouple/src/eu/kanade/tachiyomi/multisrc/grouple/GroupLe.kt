package eu.kanade.tachiyomi.multisrc.grouple

import android.content.SharedPreferences
import android.widget.Toast
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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

abstract class GroupLe(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
) : ParsedHttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addNetworkInterceptor { chain ->
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
                    "URL серии изменился. Перенесите/мигрируйте с $name " +
                        "на $name (или смежный с GroupLe), чтобы список глав обновился",
                )
            }
            response
        }
        .build()

    private var uagent = preferences.getString(UAGENT_TITLE, UAGENT_DEFAULT)!!

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
        manga.thumbnail_url =
            element.select("img.lazy").first()?.attr("data-original")?.replace("_p.", ".")
        element.select("h3 > a").first()!!.let {
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
        val url = "$baseUrl/search/advancedResults?offset=${50 * (page - 1)}"
            .toHttpUrl()
            .newBuilder()

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
    protected class MoreList(moren: List<Genre>) : Filter.Group<Genre>("Прочее", moren)
    protected class AdditionalFilterList(fils: List<Genre>) : Filter.Group<Genre>("Фильтры", fils)

    override fun mangaDetailsParse(document: Document): SManga {
        val legacyInfoElement = document.selectFirst(".expandable")
        return if (legacyInfoElement != null) {
            mangaDetailsParseLegacy(document, legacyInfoElement)
        } else {
            mangaDetailsParseModern(document)
        }
    }

    private fun mangaDetailsParseLegacy(document: Document, infoElement: Element): SManga {
        val rawCategory = infoElement.select("span.elem_category").text()
        val category = rawCategory.ifEmpty { "manga" }
        val rawAgeStop = normalizeAgeRating(
            infoElement.selectFirst(".elem_limitation .element-link")?.text().orEmpty(),
        )

        val ratingValue = (infoElement.select(".rating-block").attr("data-score").toFloatOrNull() ?: 0f) * 2
        val ratingValueOver = infoElement.select(".info-icon").attr("data-content")
            .substringBeforeLast("/5</b><br/>")
            .substringAfterLast(": <b>")
            .replace(",", ".")
            .toFloatOrNull()
            ?.times(2)
            ?: 0f
        val ratingVotes = infoElement.select(".col-sm-6 .user-rating meta[itemprop=\"ratingCount\"]")
            .attr("content")
            .ifBlank { "0" }

        val manga = SManga.create()
        manga.title = document.select(".names > .name").text()
        manga.author = infoElement.selectFirst("span.elem_author")?.text()
            ?: infoElement.selectFirst("span.elem_screenwriter")?.text()
        manga.artist = infoElement.selectFirst("span.elem_illustrator")?.text()

        val rawTags = infoElement.select("a[href*=\"/list/genre/\"], a[href*=\"/list/tag/\"]")
            .map { it.text() }

        manga.genre = listOf(category, rawAgeStop)
            .plus(rawTags)
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        val altName = infoElement.selectFirst(".another-names")?.text()
            ?.takeIf { it.isNotBlank() }
            ?.let { "Alternative names:\n$it\n\n" }
            .orEmpty()

        val descriptionText = document.select("div#tab-description .manga-description").text()
        val ratingSummary = if (ratingValue > 0f) {
            "${ratingToStars(ratingValue)} $ratingValue[$ratingValueOver] (votes: $ratingVotes)\n"
        } else {
            ""
        }
        manga.description = ratingSummary + altName + descriptionText

        val pageHtml = document.html().lowercase(Locale.ROOT)
        val badgesText = infoElement.select("span.badge").joinToString(" ") { it.text().lowercase(Locale.ROOT) }
        val hasRestrictedBanner =
            (pageHtml.contains("\u0437\u0430\u043f\u0440\u0435\u0449\u0435\u043d") && pageHtml.contains("\u043a\u043e\u043f\u0438\u0440\u0430\u0439\u0442")) ||
                (pageHtml.contains("\u0442\u0435\u0440\u0440\u0438\u0442\u043e\u0440\u0438\u0438 \u0440\u0444") && pageHtml.contains("\u0437\u0430\u043f\u0440\u0435\u0449\u0435\u043d"))

        manga.status = when {
            hasRestrictedBanner && document.select("div.chapters").isEmpty() -> SManga.LICENSED

            infoElement.html().contains("<b>\u0421\u0438\u043d\u0433\u043b") -> SManga.COMPLETED

            badgesText.contains("\u043f\u0440\u043e\u0434\u043e\u043b\u0436") || badgesText.contains("\u043d\u0430\u0447\u0430\u0442") -> SManga.ONGOING

            badgesText.contains("\u0437\u0430\u0432\u0435\u0440\u0448") -> if (badgesText.contains("\u043f\u0435\u0440\u0435\u0432\u0435\u0434\u0435\u043d")) {
                SManga.COMPLETED
            } else {
                SManga.PUBLISHING_FINISHED
            }

            badgesText.contains("\u043f\u0440\u0438\u043e\u0441\u0442") || badgesText.contains("\u0437\u0430\u043c\u043e\u0440\u043e\u0436") -> SManga.ON_HIATUS

            else -> SManga.UNKNOWN
        }

        manga.thumbnail_url = infoElement.selectFirst("img")?.let { img ->
            img.attr("data-full")
                .ifEmpty { img.attr("data-original") }
                .ifEmpty { img.attr("src") }
        }.orEmpty()

        return manga
    }

    private fun mangaDetailsParseModern(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.selectFirst(".cr-hero-names__main")?.text()
            ?: document.selectFirst("meta[itemprop=name]")?.attr("content").orEmpty()

        val details = linkedMapOf<String, String>()
        document.select(".cr-info-details__item").forEach { item ->
            val title = item.selectFirst(".cr-info-details__title")?.text()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val value = item.selectFirst(".cr-info-details__content")?.text()?.trim().orEmpty()
            if (title.isNotEmpty() && value.isNotEmpty() && !details.containsKey(title)) {
                details[title] = value
            }
        }

        val releaseStatus = details.entries.firstOrNull { it.key.contains("\u0432\u044b\u043f\u0443\u0441\u043a") }
            ?.value
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val translationStatus = details.entries.firstOrNull { it.key.contains("\u043f\u0435\u0440\u0435\u0432\u043e\u0434") }
            ?.value
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        manga.status = when {
            releaseStatus.contains("\u043f\u0440\u043e\u0434\u043e\u043b\u0436") || releaseStatus.contains("\u043d\u0430\u0447\u0430\u0442") -> SManga.ONGOING

            releaseStatus.contains("\u0437\u0430\u0432\u0435\u0440\u0448") -> if (translationStatus.contains("\u0437\u0430\u0432\u0435\u0440\u0448")) {
                SManga.COMPLETED
            } else {
                SManga.PUBLISHING_FINISHED
            }

            releaseStatus.contains("\u043f\u0440\u0438\u043e\u0441\u0442") || releaseStatus.contains("\u0437\u0430\u043c\u043e\u0440\u043e\u0436") -> SManga.ON_HIATUS

            else -> SManga.UNKNOWN
        }

        val authorNames = mutableListOf<String>()
        val artistNames = mutableListOf<String>()
        document.select(".cr-main-person-item").forEach { person ->
            val role = person.selectFirst(".cr-main-person-item__role")?.text()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val name = person.selectFirst(".cr-main-person-item__name a, .cr-main-person-item__name")
                ?.text()
                ?.trim()
                .orEmpty()

            if (name.isBlank()) return@forEach
            when {
                role.contains("\u0430\u0432\u0442\u043e\u0440") || role.contains("\u0441\u0446\u0435\u043d\u0430\u0440") -> authorNames += name
                role.contains("\u0445\u0443\u0434\u043e\u0436") || role.contains("\u0438\u043b\u043b\u044e\u0441\u0442") -> artistNames += name
            }
        }
        manga.author = authorNames.distinct().joinToString(", ").takeIf { it.isNotBlank() }
        manga.artist = artistNames.distinct().joinToString(", ").takeIf { it.isNotBlank() }

        val category = document.selectFirst(".cr-hero-short-details a[href*=\"/list/category/\"]")
            ?.text()
            .orEmpty()
        val age = normalizeAgeRating(
            document.selectFirst(".cr-hero-short-details a[href*=\"/list/limitation/\"]")
                ?.text()
                .orEmpty(),
        )
        val tags = document.select(".cr-tags .cr-tags__item").mapNotNull { tag ->
            tag.select("span").last()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        }

        manga.genre = listOf(category, age)
            .plus(tags)
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")

        val altNames = document.select("#alt-names-dialog .modal-body .py-1")
            .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
            .distinct()
        val altNameText = if (altNames.isEmpty()) {
            ""
        } else {
            "Alternative names:\n${altNames.joinToString(" / ")}\n\n"
        }

        val ratingValue = document.selectFirst(".cr-hero-rating__main .cr-hero-rating__value")
            ?.text()
            ?.replace(",", ".")
            ?.toFloatOrNull()
            ?: 0f
        val ratingVotes = document.selectFirst(".cr-hero-rating__votes")
            ?.text()
            ?.filter { it.isDigit() }
            .orEmpty()
            .ifBlank { "0" }
        val ratingSummary = if (ratingValue > 0f) {
            "${ratingToStars(ratingValue)} $ratingValue (votes: $ratingVotes)\n"
        } else {
            ""
        }

        val descriptionText = document.selectFirst(".cr-description__content")
            ?.text()
            .orEmpty()
        manga.description = ratingSummary + altNameText + descriptionText

        val thumbElement = document.selectFirst(".cr-hero-poster__img")
            ?: document.selectFirst(".cr-hero-overlay__bg")
        manga.thumbnail_url = thumbElement?.let { element ->
            element.attr("src")
                .ifEmpty { element.attr("data-src") }
                .ifEmpty { element.attr("data-original") }
                .ifEmpty { element.attr("data-bg") }
        }.orEmpty()

        return manga
    }

    private fun normalizeAgeRating(rawAgeValue: String): String = when (rawAgeValue) {
        "NC-17", "R18+" -> "18+"
        "R", "G", "PG" -> "16+"
        "PG-13" -> "12+"
        else -> rawAgeValue
    }

    private fun ratingToStars(ratingValue: Float): String = when {
        ratingValue > 9.5f -> "*****"
        ratingValue > 8.5f -> "****+"
        ratingValue > 7.5f -> "****-"
        ratingValue > 6.5f -> "***+-"
        ratingValue > 5.5f -> "***--"
        ratingValue > 4.5f -> "**+--"
        ratingValue > 3.5f -> "**---"
        ratingValue > 2.5f -> "*+---"
        ratingValue > 1.5f -> "*----"
        ratingValue > 0.5f -> "+----"
        else -> "-----"
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = if (manga.status != SManga.LICENSED) {
        client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
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

        if (document.select(".user-avatar").isEmpty() &&
            document.title().run { contains("AllHentai") || contains("MintManga") || contains("МинтМанга") || contains("RuMix") }
        ) {
            throw Exception("Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E")
        }

        val chapterSearchParams = getChapterSearchParams(document)

        return document.select(chapterListSelector()).map { chapterFromElement(it, manga, chapterSearchParams) }
    }

    override fun chapterListSelector() = "tr.item-row:has(td > a):has(td.date:not(.text-info))"

    private fun chapterFromElement(element: Element, manga: SManga, chapterSearchParams: String): SChapter {
        val urlElement = element.select("a.chapter-link").first()!!
        val chapterInf = element.select("td.item-title").first()!!
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

        chapter.date_upload = element.select("td.d-none").last()?.text()?.let {
            if (it.isEmpty()) {
                0L
            } else {
                try {
                    SimpleDateFormat("dd.MM.yy", Locale.US).parse(it)?.time ?: 0L
                } catch (e: ParseException) {
                    SimpleDateFormat("dd/MM/yy", Locale.US).parse(it)?.time ?: 0L
                }
            }
        } ?: 0
        return chapter
    }

    protected open fun chapterScanlatorFromElement(chapterLinkElement: Element, chapterRowElement: Element): String {
        val translatorElement = chapterLinkElement.attr("title")
        return if (translatorElement.isNotBlank()) {
            translatorElement
                .replace("(Переводчик),", "&")
                .removeSuffix(" (Переводчик)")
        } else {
            ""
        }
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
                        " - " + DecimalFormat("#,###.##").format(chapter.chapter_number)
                            .replace(",", ".") + " ",
                    )
                }
            }

            single.containsMatchIn(chapter.name) -> {
                if (chapter.name.substringAfter("Сингл").trim().isEmpty()) {
                    chapter.name = DecimalFormat("#,###.##").format(chapter.chapter_number)
                        .replace(",", ".") + " " + chapter.name
                }
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val html = document.html()

        if (document.select(".user-avatar").isEmpty() &&
            document.title().run { contains("AllHentai") || contains("MintManga") || contains("МинтМанга") || contains("RuMix") }

        ) {
            throw Exception("Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E")
        }

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

    private fun searchMangaByIdRequest(id: String): Request = GET("$baseUrl/$id", headers)

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = if (query.startsWith(PREFIX_SLUG_SEARCH)) {
        val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)
        client.newCall(searchMangaByIdRequest(realQuery))
            .asObservableSuccess()
            .map { response ->
                val details = mangaDetailsParse(response)
                details.url = "/$realQuery"
                MangasPage(listOf(details), false)
            }
    } else {
        client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(UAGENT_TITLE, UAGENT_DEFAULT, uagent))
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(
        title: String,
        default: String,
        value: String,
    ): androidx.preference.EditTextPreference = androidx.preference.EditTextPreference(context).apply {
        key = title
        this.title = title
        summary = value
        this.setDefaultValue(default)
        dialogTitle = title
        setOnPreferenceChangeListener { _, newValue ->
            try {
                val res = preferences.edit().putString(title, newValue as String).commit()
                Toast.makeText(
                    context,
                    "Для смены User-Agent необходимо перезапустить приложение с полной остановкой.",
                    Toast.LENGTH_LONG,
                ).show()
                res
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    companion object {
        private const val UAGENT_TITLE = "User-Agent(для некоторых стран)"
        private const val UAGENT_DEFAULT = "arora"
        const val PREFIX_SLUG_SEARCH = "slug:"
        private val USER_HASH_REGEX = "user_hash.+'(.+)'".toRegex()
    }
}
