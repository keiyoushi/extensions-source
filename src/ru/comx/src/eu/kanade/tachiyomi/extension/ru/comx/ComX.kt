package eu.kanade.tachiyomi.extension.ru.comx

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class ComX :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(DleGuardResolver.interceptor(baseUrl))
        rateLimit(3)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Sec-Fetch-Dest", "document")
        set("Sec-Fetch-Mode", "navigate")
        set("Sec-Fetch-Site", "none")
        set("Sec-Fetch-User", "?1")
    }

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage = searchCatalog(page, "rating")

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = searchCatalog(page, "editdate")

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addPathSegment(query)
                addPathSegments("page/$page")
                addPathSegment("")
            }.build()
            return parseSearchMangas(client.get(url))
        }

        var orderBy = "rating"
        var ascEnd = "desc"
        val checkYear = Calendar.getInstance().get(Calendar.YEAR)
        var yearFrom = 1980
        var yearTo = checkYear

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("ComicList")
            filters.forEach { filter ->
                when (filter) {
                    is OrderBy -> {
                        orderBy = filter.selected
                        ascEnd = filter.order
                    }
                    is GroupFilter -> {
                        filter.included?.let { addPathSegment("p.cat=${it.joinToString(",")}") }
                        filter.excluded?.let { addPathSegment("exc_p.cat=${it.joinToString(",")}") }
                    }
                    is GenreFilter -> {
                        filter.included?.let { addPathSegment("g=${it.joinToString(",")}") }
                        filter.excluded?.let { addPathSegment("exc_g=${it.joinToString(",")}") }
                    }
                    is TypeFilter -> {
                        filter.included?.let { addPathSegment("t=${it.joinToString(",")}") }
                        filter.excluded?.let { addPathSegment("exc_t=${it.joinToString(",")}") }
                    }
                    is StatusFilter -> {
                        filter.included?.let { addPathSegment("st=${it.joinToString(",")}") }
                        filter.excluded?.let { addPathSegment("exc_st=${it.joinToString(",")}") }
                    }
                    is YearRangeFilter -> {
                        filter.minValue?.let { yearFrom = checkMinRange(it, max = checkYear) }
                        filter.maxValue?.let { yearTo = checkMaxRange(it, max = checkYear) }
                    }
                    else -> {}
                }
            }
            // Без этих сегментов происходит зацикленное перенаправление на comix-read
            addPathSegment("y[from]=$yearFrom")
            addPathSegment("y[to]=$yearTo")
            if (page > 1) {
                addPathSegments("page/$page")
            }
            addPathSegment("")
        }.build()

        val body = FormBody.Builder()
            .add("dlenewssortby", orderBy)
            .add("dledirection", ascEnd)
            .add("set_new_sort", "dle_sort_xfilter")
            .add("set_direction_sort", "dle_direction_xfilter")
            .build()

        return parseSearchMangas(client.post(url, body))
    }

    // ============================== Search Utilities ===============================
    private suspend fun searchCatalog(page: Int, sortBy: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("comix-read")
            if (page > 1) {
                addPathSegments("page/$page")
            }
            addPathSegment("")
        }.build()

        val body = FormBody.Builder()
            .add("dlenewssortby", sortBy)
            .add("dledirection", "desc")
            .add("set_new_sort", "dle_sort_cat_1")
            .add("set_direction_sort", "dle_direction_cat_1")
            .build()

        return parseSearchMangas(client.post(url, body))
    }

    fun parseSearchMangas(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#dle-content > .readed").map { element ->
            SManga.create().apply {
                with(element.selectFirst(".readed__title > a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    title = ownText().replace(" / ", " | ").substringAfterLast(" | ").trim()
                }
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("div.pagination__pages")
            ?.children()?.last()?.tagName() == "a"

        return MangasPage(mangas, hasNextPage)
    }
    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") && absUrl("data-src").isNotBlank() && absUrl("data-src").contains("://") -> absUrl("data-src")
        hasAttr("data-src") && absUrl("data-src").isNotBlank() && !absUrl("data-src").contains("://") -> baseUrl + absUrl("data-src")
        absUrl("src").contains("://") -> absUrl("src")
        else -> baseUrl + absUrl("src")
    }

    // ============================== Manga ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        url.pathSegments.firstOrNull() ?: return null
        return parseMangaDetails(client.get(url).asJsoup(), url.encodedPath)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(baseUrl + manga.url, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403) {
                    throw Exception("Контент не доступен. Возможно может помочь авторизация через WebView")
                } else {
                    throw HttpException(response.code)
                }
            }
            response.asJsoup()
        }
        return SMangaUpdate(parseMangaDetails(doc, manga.url), parseChapterList(doc))
    }

    private fun parseMangaDetails(doc: Document, mangaUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = doc.selectFirst("header.page__header h1")!!.text()
        thumbnail_url = doc.selectFirst("div.page__poster img")?.imgAttr()

        val ratingValue = (doc.selectFirst(".page__activity-votes")?.ownText()?.trim()?.toFloatOrNull() ?: 0f) * 2
        val ratingVotes = doc.selectFirst(".page__activity-votes span > span")?.text()?.trim() ?: "0"
        val ratingStar = when {
            ratingValue > 9.5 -> "★★★★★"
            ratingValue > 8.5 -> "★★★★✬"
            ratingValue > 7.5 -> "★★★★☆"
            ratingValue > 6.5 -> "★★★✬☆"
            ratingValue > 5.5 -> "★★★☆☆"
            ratingValue > 4.5 -> "★★✬☆☆"
            ratingValue > 3.5 -> "★★☆☆☆"
            ratingValue > 2.5 -> "★✬☆☆☆"
            ratingValue > 1.5 -> "★☆☆☆☆"
            ratingValue > 0.5 -> "✬☆☆☆☆"
            else -> "☆☆☆☆☆"
        }

        description = buildString {
            doc.selectFirst(".page__title-original")?.text()?.takeIf { it.isNotBlank() }?.let {
                append(it)
                appendLine()
            }
            if (doc.getPageListItem("Тип выпуска")?.contains("ХРОНОЛОГИЯ") == true) {
                append("Cобытие в комиксах - ХРОНОЛОГИЯ")
                appendLine()
            }
            append(ratingStar).append(" ").append(ratingValue).append(" (голосов: ").append(ratingVotes).append(")\n")
            appendLine()
            append(doc.selectFirst("div.page__text")?.text())
        }

        author = doc.getPageListItem("Автор")
        artist = doc.getPageListItem("Художник")

        genre = buildList {
            add(parseCategory(doc.select(".speedbar a").last()?.text()?.trim() ?: ""))
            addAll(doc.select("div.page__tags a").eachText())
        }.joinToString()

        status = parseStatus(doc.getPageListItem("Статус"))

        doc.selectFirst("#rec-similar")?.select("a.poster")?.let { anchor ->
            memo = buildJsonObject {
                val similar = anchor.mapNotNull { element ->
                    val title = element.selectFirst(".poster__title")?.text()?.takeIf { it.isNotBlank() }
                    val url = element.attr("href").takeIf { it.isNotBlank() }
                    val thumb = element.selectFirst("img")?.imgAttr()
                    if (title != null && url != null) RelatedComic(title, url, thumb) else null
                }

                put("similarComics", similar.toJsonElement())
            }
        }
    }

    // ============================== Manga Utilities ===============================
    private fun Document.getPageListItem(label: String): String? = selectFirst(".page__list > li:has(> div:contains($label))")?.let { element ->
        element.selectFirst("a")?.text() ?: element.ownText()
    }?.takeIf { it.isNotBlank() }

    private fun parseStatus(element: String?): Int = when {
        element.isNullOrBlank() -> SManga.UNKNOWN
        element.contains("Продолжается") || element.contains(" из ") || element.contains("Онгоинг") -> SManga.ONGOING
        element.contains("перевод продолжается") -> SManga.PUBLISHING_FINISHED
        element.contains("Заверш") || element.contains("Лимитка") || element.contains("Ван шот") || element.contains("Графический роман") -> SManga.COMPLETED
        element.contains("Заморожен") || element.contains("Приостановлен") -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseCategory(cat: String) = when (cat.lowercase()) {
        "manga" -> "Манга"
        "manhwa" -> "Манхва"
        "manhua" -> "Маньхуа"
        else -> "Комикс"
    }

    // ============================== Chapters ===============================
    private fun parseChapterList(document: Document): List<SChapter> {
        if (document.selectFirst(".message-info__content:contains(не имеют доступа)") != null) throw Exception("Авторизируйтесь для просмотра контента")
        val script = document.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: throw Exception("Chapter data script not found")

        val data = script
            .substringAfter("window.__DATA__ = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<Chapters>()

        var counter = 0f
        var firstChapter = true

        return data.chapters.asReversed().map { chap ->
            SChapter.create().apply {
                url = "/reader/${data.comicId}/${chap.id}"
                date_upload = dateFormat.tryParseDate(chap.date)

                val matchNumber = chapterNumberRegex.find(chap.title)?.groupValues[1]?.toFloatOrNull()
                val anyNumber = chapterAnyNumberRegex.find(chap.title)?.groupValues[1]?.toFloatOrNull()
                chapter_number = if (!firstChapter) {
                    if (chap.number != 0f) {
                        // Номер не надежен. Он может быть неверным, например:
                        // https://com-x.life/11082-chelovek-benzopila-2-2026.html#chapters
                        // Глава 158. Или Экстра 17
                        if (matchNumber != null && (matchNumber - counter) in 0f..1f) {
                            matchNumber
                        } else {
                            // Extra have a certain word in title or contains exactly `# 1` in title.
                            // Regex should exclude any #1.0, #12 but match `#1-`, `#1:` or `# 1 `.
                            if (isExtraChapter(chap.title) || noInfoExtra.containsMatchIn(chap.title)) {
                                counter + 0.1f
                            } else {
                                if (anyNumber != null && (anyNumber - counter) in 0f..1f) {
                                    anyNumber
                                } else {
                                    chap.number
                                }
                            }
                        }
                    } else {
                        if (anyNumber != null && (anyNumber - counter) in 0f..1f) {
                            anyNumber
                        } else {
                            counter + 0.1f
                        }
                    }
                } else {
                    firstChapter = false
                    chap.number
                }
                name = whitespacesRegex.replace(chap.title, " ").trim()
                counter = chapter_number
            }
        }.asReversed()
    }

    private fun isExtraChapter(title: String): Boolean {
        val lower = title.lowercase()
        return "экстра" in lower ||
            "ежегодник" in lower ||
            "вернулся" in lower ||
            "extra" in lower ||
            "special" in lower ||
            "annual" in lower ||
            "bonus" in lower
    }

    // ============================== Related ==============================
    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val related = manga.memo["similarComics"]

        return related?.parseAs<List<RelatedComic>>()?.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.thumbnail
                setUrlWithoutDomain(it.url)
            }
        } ?: emptyList()
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}", ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 304 || response.code == 302 || response.code == 403) {
                    throw Exception("Глава не доступна. Возможно может помочь авторизация через WebView")
                } else {
                    throw HttpException(response.code)
                }
            }
            if (response.request.url.encodedPath == "/404.html") {
                throw Exception("Глава не доступна. Возможно может помочь авторизация через WebView")
            }
            response.asJsoup()
        }

        if (document.html().contains("Выпуск был удален по требованию правообладателя")) throw Exception("Лицензировано. Возможно может помочь авторизация через WebView")

        val script = document.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: throw Exception("Pages data script not found")

        val data = script
            .substringAfter("window.__DATA__ = ")
            .substringBefore("window.")
            .substringBeforeLast(";")
            .trim()
            .parseAs<Pages>()

        // Сайт использует два домена для изображений: img.com-x.life (глобальный) и rus.com-x.life (для россии)
        // Домен из настроек в приоритете, если задан.
        val imageUrl = preferences.getString(FORCE_IMG_DOMAIN_PREF, null)?.takeIf { it.isNotBlank() }
            ?: if (baseUrl.contains("https://ru.")) {
                "https://${data.hostRu}/comix/"
            } else {
                "https://${data.host}/comix/"
            }

        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = "$imageUrl$img")
        }
    }

    // =============================== Filters ===============================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val doc = client.get("$baseUrl/comix-read/").asJsoup()
        val script = doc.selectFirst("script:containsData(window.__XFILTER__)")?.data()
            ?: error("Filter data not found")

        val data = script
            .substringAfter("window.__XFILTER__ = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<FiltersJSON>()

        return FiltersDto(
            pcat = data.filterItems.pCat.values.map { it.value to it.id.toString() },
            g = data.filterItems.g.values.map { it.value to it.id.toString() },
            t = data.filterItems.t.values.map { it.value to it.id.toString() },
            st = data.filterItems.st.values.map { it.value to it.id.toString() },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val dto = data?.parseAs<FiltersDto>()
        val genres = dto?.g ?: emptyList()
        val groups = dto?.pcat ?: emptyList()
        val types = dto?.t ?: emptyList()
        val statuses = dto?.st ?: emptyList()

        val filters = mutableListOf(
            Filter.Header("Фильтры не работают при поиске по названию"),
            Filter.Separator(),
            OrderBy(),
        )

        if (genres.isNotEmpty()) {
            filters.add(GenreFilter(genres))
        }
        if (groups.isNotEmpty()) {
            filters.add(GroupFilter(groups))
        }
        if (types.isNotEmpty()) {
            filters.add(TypeFilter(types))
        }
        if (statuses.isNotEmpty()) {
            filters.add(StatusFilter(statuses))
        }
        filters.add(YearRangeFilter())

        return FilterList(filters)
    }

    // ============================== Utilities ===============================
    private fun checkMinRange(input: String?, min: Int = 1980, max: Int): Int {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return min
        if (value !in min..max) return min
        return value
    }
    private fun checkMaxRange(input: String?, min: Int = 1980, max: Int): Int {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return max
        if (value !in min..max) return max
        return value
    }

    // ============================== Preferences ===============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = FORCE_IMG_DOMAIN_PREF
            title = "Домен картинок"
            summary = "Если изображения не грузятся очистите «Кэш приложения» и всевозможные данные в настройках приложения  (Настройки -> Дополнительно) \nи перезапустите приложение с полной остановкой" +
                "\n\nНастройка переопределяет домен картинок." +
                "\nПо умолчанию домен картинок берётся автоматически." +
                "\nОставьте это поле пустым что бы использовать домен по умолчанию." +
                "\nЧтобы узнать домен изображения откройте главу в браузере и \nпосле долгим тапом откройте изображение в новом окне."
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("[d.M.yyyy][dd.MM.yyyy][d.MM.yyyy]", Locale.ROOT)
        private const val FORCE_IMG_DOMAIN_PREF = "FORCE_IMG_DOMAIN_PREF"
        private val chapterNumberRegex = """(?:\d+\s*-|.*?Глава)\s*([\d.]+)""".toRegex()
        private val chapterAnyNumberRegex = """([\d.]+)""".toRegex()
        private val noInfoExtra = """#\s?1(?!\d|\.\d)""".toRegex()
        private val whitespacesRegex = """\s{2,}""".toRegex()
    }
}
