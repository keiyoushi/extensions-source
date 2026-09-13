package eu.kanade.tachiyomi.extension.ru.allhentai

import eu.kanade.tachiyomi.multisrc.grouple.FiltersAPIResponse
import eu.kanade.tachiyomi.multisrc.grouple.FiltersData
import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.multisrc.grouple.YearsData
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import org.jsoup.nodes.Document

@Source
abstract class AllHentai : GroupLe() {

    override val siteId: Int get() = if (baseUrl == "https://x.ahen.me") {
        11
    } else {
        1
    }
    override val authApiUrl: String get() = "https://z.qawa.org"

    override fun authGuard(document: Document) {
        document.select("script:containsData(UI.ViewContext)").joinToString().let {
            if (it.contains("useContext('blockedForAnonymous") && document.selectFirst(".user-avatar") == null) {
                throw Exception("Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E")
            }
        }
    }

    // =========================== Manga ============================
    override val tagsSelector: String = ".cr-tags .cr-tags__item:not(.cr-tags__item--misc) span:not(.text-secondary)"
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // ahen.me sometimes sets url as id, for manga with paid chapters, while allhen.online can have only slug in url.
        // If baseUrl are not ahen.me and url are only numbers -> it's an old entry from ahen.me opened with new base url
        // Throw message to migrate
        if (baseUrl == "https://20.allhen.online" && manga.url.matches(checkManga)) {
            throw Exception("URL серии изменился. Перенесите/мигрируйте с $name на $name, чтобы обновить информацию")
        }
        return super.fetchMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
    }

    // =========================== Related Manga (Komikku) ============================
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = "$baseUrl/list/like${manga.url.substringBefore("__")}"
        val document = client.get(url).asJsoup()
        return document.select(".tiles .tile").mapNotNull {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst(".desc h3 a")?.absUrl("href") ?: return@mapNotNull null)
                title = it.selectFirst(".desc h3 a")?.attr("title") ?: return@mapNotNull null
                thumbnail_url = it.selectFirst("img.img-fluid")?.attr("data-original") ?: ""
            }
        }
    }

    // =========================== Search ============================
    // Сайт все еще использует старую форму поиска, но API уже работает.
    // Удалить все это, если сайт начнет использовать новый дизайн/api в поиске, для автоматического получения данных.
    override suspend fun fetchFilterData(): JsonElement = FiltersData(
        sortType = getSortBy,
        productionStatus = getStatus,
        tags = getTags(),
        translationStatus = getTranslationStatus,
        searchFilters = getAdditionalFilters,
        genre = getGenreList,
        category = getCategoryList,
        limitation = emptyList(),
        another = emptyList(),
        years = YearsData(1988, 2027),
    ).toJsonElement()

    private suspend fun getTags(): List<Pair<String, String>>? {
        val result = client.get("$baseUrl/api/catalog/elementsByType?type=40").parseAs<FiltersAPIResponse>()
        return result.results?.map { it.text to it.id }
    }
    private val getAdditionalFilters = listOf(
        Pair("Высокий рейтинг", "HIGH_RATE"),
        Pair("Сингл", "SINGLE"),
        Pair("Для взрослых", "MATURE"),
        Pair("Переведено", "TRANSLATED"),
        Pair("Заброшен перевод", "ABANDONED_POPULAR"),
        Pair("Длинная", "MANY_CHAPTERS"),
        Pair("Ожидает загрузки", "WAIT_UPLOAD"),
    )

    private val getCategoryList = listOf(
        Pair("3D", "626"),
        Pair("Анимация", "5777"),
        Pair("Без текста", "3157"),
        Pair("Комикс", "1003"),
        Pair("Манга", "6449"),
        Pair("Манхва", "1104"),
        Pair("Маньхуа", "5902"),
        Pair("Руманга", "5896"),
    )

    private val getSortBy = listOf(
        Pair("По популярности", "RATING"),
        Pair("По алфавиту", "NAME"),
        // Результат поиска не соотв сайту
        // Pair("По году написания", "YEAR"),
        Pair("Популярность сейчас", "POPULARITY"),
        Pair("По рейтингу", "USER_RATING"),
        Pair("Новинки", "DATE_CREATE"),
        Pair("По дате обновления", "DATE_UPDATE"),
    )

    private val getStatus = listOf(
        Pair("Запланирован", "PLANNED"),
        Pair("Продолжается", "PROGRESS"),
        Pair("Приостановлен", "POSTPONED"),
        Pair("Отменён", "CANCELED"),
        Pair("Завершён", "FINISHED"),
        Pair("Не окончен", "NON_FINISHED"),
    )

    private val getTranslationStatus = listOf(
        Pair("Отсутствует", "NONE"),
        Pair("Начат", "STARTED"),
        Pair("Продолжается", "PROGRESS"),
        Pair("Приостановлен", "POSTPONED"),
        Pair("Завершён", "FINISHED"),
        Pair("Нет необходимости", "NO_NEED"),
    )

    private val getGenreList = listOf(
        Pair("Ahegao", "855"),
        Pair("Анал", "828"),
        Pair("Бдсм", "78"),
        Pair("Без цензуры", "888"),
        Pair("Большая грудь", "837"),
        Pair("Большая попка", "3156"),
        Pair("Большой член", "884"),
        Pair("Бондаж", "5754"),
        Pair("В первый раз", "811"),
        Pair("В цвете", "290"),
        Pair("Гарем", "87"),
        Pair("Гендарная интрига", "89"),
        Pair("Групповой секс", "88"),
        Pair("Драма", "95"),
        Pair("Зрелые женщины", "5679"),
        Pair("Измена", "291"),
        Pair("Изнасилование", "124"),
        Pair("Инцест", "85"),
        Pair("Исторический", "93"),
        Pair("Комедия", "73"),
        Pair("Маленькая грудь", "870"),
        Pair("Научная фантастика", "76"),
        Pair("Нетораре", "303"),
        Pair("Оральный секс", "853"),
        Pair("Романтика", "74"),
        Pair("Тентакли", "69"),
        Pair("Трагедия", "1321"),
        Pair("Ужасы", "75"),
        Pair("Футанари", "77"),
        Pair("Фэнтези", "70"),
        Pair("Чикан", "1059"),
        Pair("Этти", "798"),
    )

    companion object {
        private val checkManga = """^/\d+$""".toRegex()
    }
}
