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
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.selectLast
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
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
        addInterceptor(ImageHostInterceptor())
        rateLimit(3)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = searchCatalog(page, "rating")

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchCatalog(page, "editdate")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addPathSegment(query)
                addPathSegments("page/$page")
                addPathSegment("")
            }.build()
            return parseSearchMangas(client.get(url, Fetch.DOCUMENT))
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

        return parseSearchMangas(client.post(url, Fetch.DOCUMENT, body))
    }

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

        return parseSearchMangas(client.post(url, Fetch.DOCUMENT, body))
    }

    private fun parseSearchMangas(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#dle-content > .readed").map { element ->
            SManga.create().apply {
                with(element.selectFirst(".readed__title > a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    title = ownText().localTitle()
                }
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("div.pagination__pages")
            ?.children()?.last()?.tagName() == "a"

        return MangasPage(mangas, hasNextPage)
    }

    // Russian title from "Original / Russian"
    private fun String.localTitle() = replace(" / ", " | ").substringAfterLast(" | ").trim()

    private fun Element.imgAttr(): String? = attrOrNull("abs:data-src") ?: attrOrNull("abs:src")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.endsWith(".html")) return null
        val manga = SManga.create().apply { this.url = url.encodedPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403) {
                    throw Exception("Контент не доступен. Возможно может помочь авторизация через WebView")
                } else {
                    throw HttpException(response.code)
                }
            }
            response.asJsoup()
        }
        return SMangaUpdate(parseMangaDetails(document, manga.url), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = document.selectFirst("header.page__header h1")!!.text()
        thumbnail_url = document.selectFirst("div.page__poster img")?.imgAttr()

        description = MarkdownDescription.Builder()
            .rating(
                document.selectFirst(".page__activity-votes")?.ownText()?.toFloatOrNull()?.times(2),
                votes = document.selectFirst(".page__activity-votes span > span")?.text()?.toIntOrNull(),
            )
            .info(document.getPageListItem("Год"), document.getPageListItem("Издатель"))
            .separator()
            .description(document.selectFirst("div.page__text"))
            .field("Тип выпуска", document.getPageListItem("Тип выпуска"))
            .altTitles(listOfNotNull(document.selectFirst(".page__title-original")?.textOrNull()), "Оригинальное название")
            .build()

        author = document.getPageListItem("Автор")
        artist = document.getPageListItem("Художник")

        genre = buildList {
            add(parseCategory(document.selectLast(".speedbar a")?.text().orEmpty()))
            addAll(document.select("div.page__tags a").eachText())
        }.joinToString()

        status = parseStatus(document.getPageListItem("Статус"))

        val related = document.select("#dle-content a.poster").mapNotNull { element ->
            val title = element.selectFirst(".poster__title")?.textOrNull()?.localTitle() ?: return@mapNotNull null
            val url = element.attrOrNull("abs:href") ?: return@mapNotNull null
            RelatedComic(title, url, element.selectFirst("img")?.imgAttr())
        }.distinctBy { it.url }
        memo = buildJsonObject { put("similarComics", related.toJsonElement()) }
    }

    private fun Document.getPageListItem(label: String): String? = selectFirst(".page__list > li:has(> div:contains($label))")?.let { element ->
        element.selectFirst("a")?.text() ?: element.ownText()
    }?.takeIf { it.isNotBlank() }

    private fun parseStatus(status: String?): Int {
        val lower = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "перевод продолжается" in lower -> SManga.PUBLISHING_FINISHED
            "продолжается" in lower || " из " in lower || "онгоинг" in lower -> SManga.ONGOING
            "заверш" in lower || "лимитка" in lower || "ван шот" in lower || "графический роман" in lower -> SManga.COMPLETED
            "заморожен" in lower || "приостановлен" in lower -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun parseCategory(cat: String) = when (cat.lowercase()) {
        "manga" -> "Манга"
        "manhwa" -> "Манхва"
        "manhua" -> "Маньхуа"
        else -> "Комикс"
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        if (document.selectFirst(".message-info__content:contains(не имеют доступа)") != null) throw Exception("Авторизируйтесь для просмотра контента")
        val script = document.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: throw Exception("Не найдены данные глав")

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
                name = whitespacesRegex.replace(Parser.unescapeEntities(chap.title, false), " ").trim()
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT, ensureSuccess = false).use { response ->
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
            ?: throw Exception("Не найдены данные страниц")

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

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl.toHttpUrl().newBuilder().addPathSegments("comix-read/").build(), Fetch.DOCUMENT).asJsoup()
        val script = document.selectFirst("script:containsData(window.__XFILTER__)")?.data()
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
