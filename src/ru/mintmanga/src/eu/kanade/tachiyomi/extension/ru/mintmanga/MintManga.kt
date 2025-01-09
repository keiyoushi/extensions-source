package eu.kanade.tachiyomi.extension.ru.mintmanga

import android.app.Application
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MintManga : GroupLe("MintManga", "https://2.mintmanga.one", "ru") {

    override val id: Long = 6

    private val preferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val baseUrl by lazy { getPrefBaseUrl() }

    override fun getChapterSearchParams(document: Document): String {
        val scriptContent = document.selectFirst("script:containsData(user_hash)")?.data()

        val userHash = scriptContent?.let { USER_HASH_REGEX.find(it)?.groupValues?.get(1) }

        return userHash?.let { "?d=$it" } ?: ""
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = super.searchMangaRequest(page, query, filters).url.newBuilder()
        (if (filters.isEmpty()) getFilterList().reversed() else filters.reversed()).forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(genre.id, arrayOf("=", "=in", "=ex")[genre.state])
                    }
                }
                is Category -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(category.id, arrayOf("=", "=in", "=ex")[category.state])
                    }
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(age.id, arrayOf("=", "=in", "=ex")[age.state])
                    }
                }
                is More -> filter.state.forEach { more ->
                    if (more.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(more.id, arrayOf("=", "=in", "=ex")[more.state])
                    }
                }
                is FilList -> filter.state.forEach { fils ->
                    if (fils.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(fils.id, arrayOf("=", "=in", "=ex")[fils.state])
                    }
                }
                is OrderBy -> {
                    if (url.toString().contains("&") && filter.state < 6) {
                        url.addQueryParameter("sortType", arrayOf("RATING", "POPULARITY", "YEAR", "NAME", "DATE_CREATE", "DATE_UPDATE")[filter.state])
                    } else {
                        val ord = arrayOf("rate", "popularity", "year", "name", "created", "updated", "votes")[filter.state]
                        return GET("$baseUrl/list?sortType=$ord&offset=${70 * (page - 1)}", headers)
                    }
                }
                else -> {}
            }
        }
        return if (url.toString().contains("&")) {
            GET(url.toString().replace("=%3D", "="), headers)
        } else {
            popularMangaRequest(page)
        }
    }

    private class OrderBy : Filter.Select<String>(
        "Сортировка",
        arrayOf("По популярности", "Популярно сейчас", "По году", "По имени", "Новинки", "По дате обновления", "По рейтингу"),
    )

    private class Genre(name: String, val id: String) : Filter.TriState(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)
    private class Category(categories: List<Genre>) : Filter.Group<Genre>("Категории", categories)
    private class AgeList(ages: List<Genre>) : Filter.Group<Genre>("Возрастная рекомендация", ages)
    private class More(moren: List<Genre>) : Filter.Group<Genre>("Прочее", moren)
    private class FilList(fils: List<Genre>) : Filter.Group<Genre>("Фильтры", fils)

    override fun getFilterList() = FilterList(
        OrderBy(),
        Category(getCategoryList()),
        GenreList(getGenreList()),
        AgeList(getAgeList()),
        More(getMore()),
        FilList(getFilList()),
    )
    private fun getFilList() = listOf(
        Genre("Высокий рейтинг", "s_high_rate"),
        Genre("Сингл", "s_single"),
        Genre("Для взрослых", "s_mature"),
        Genre("Завершенная", "s_completed"),
        Genre("Переведено", "s_translated"),
        Genre("Заброшен перевод", "s_abandoned_popular"),
        Genre("Длинная", "s_many_chapters"),
        Genre("Ожидает загрузки", "s_wait_upload"),
        Genre("Белые жанры", "s_not_pessimized"),
    )
    private fun getMore() = listOf(
        Genre("Анонс", "el_6641"),
        Genre("В цвете", "el_4614"),
        Genre("Веб", "el_1355"),
        Genre("Выпуск приостановлен", "el_5232"),
        Genre("Не Яой", "el_1874"),
        Genre("Сборник", "el_1348"),
    )

    private fun getAgeList() = listOf(
        Genre("R(16+)", "el_3968"),
        Genre("NC-17(18+)", "el_3969"),
        Genre("R18+(18+)", "el_3990"),
    )

    private fun getCategoryList() = listOf(
        Genre("OEL-манга", "el_6637"),
        Genre("Додзинси", "el_1332"),
        Genre("Арт", "el_2220"),
        Genre("Ёнкома", "el_2741"),
        Genre("Комикс", "el_1903"),
        Genre("Манга", "el_6421"),
        Genre("Манхва", "el_1873"),
        Genre("Маньхуа", "el_1875"),
        Genre("Ранобэ", "el_5688"),
    )

    private fun getGenreList() = listOf(
        Genre("боевик", "el_1346"),
        Genre("боевые искусства", "el_1334"),
        Genre("гарем", "el_1333"),
        Genre("гендерная интрига", "el_1347"),
        Genre("героическое фэнтези", "el_1337"),
        Genre("детектив", "el_1343"),
        Genre("дзёсэй", "el_1349"),
        Genre("драма", "el_1310"),
        Genre("игра", "el_5229"),
        Genre("исэкай", "el_6420"),
        Genre("история", "el_1311"),
        Genre("киберпанк", "el_1351"),
        Genre("комедия", "el_1328"),
        Genre("меха", "el_1318"),
        Genre("научная фантастика", "el_1325"),
        Genre("омегаверс", "el_5676"),
        Genre("повседневность", "el_1327"),
        Genre("постапокалиптика", "el_1342"),
        Genre("приключения", "el_1322"),
        Genre("психология", "el_1335"),
        Genre("романтика", "el_1313"),
        Genre("самурайский боевик", "el_1316"),
        Genre("сверхъестественное", "el_1350"),
        Genre("сёдзё", "el_1314"),
        Genre("сёдзё-ай", "el_1320"),
        Genre("сёнэн", "el_1326"),
        Genre("сёнэн-ай", "el_1330"),
        Genre("спорт", "el_1321"),
        Genre("сэйнэн", "el_1329"),
        Genre("сянься", "el_6631"),
        Genre("трагедия", "el_1344"),
        Genre("триллер", "el_1341"),
        Genre("ужасы", "el_1317"),
        Genre("уся", "el_6632"),
        Genre("фэнтези", "el_1323"),
        Genre("школа", "el_1319"),
        Genre("эротика", "el_1340"),
        Genre("этти", "el_1354"),
        Genre("юри", "el_1315"),
        Genre("яой", "el_1336"),
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = DOMAIN_TITLE
            setDefaultValue(super.baseUrl)
            dialogTitle = DOMAIN_TITLE
            dialogMessage = "Default URL:\n\t${super.baseUrl}"
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Для смены домена необходимо перезапустить приложение с полной остановкой.", Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(DOMAIN_PREF, super.baseUrl)!!

    init {
        preferences.getString(DEFAULT_DOMAIN_PREF, null).let { defaultBaseUrl ->
            if (defaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(DOMAIN_PREF, super.baseUrl)
                    .putString(DEFAULT_DOMAIN_PREF, super.baseUrl)
                    .apply()
            }
        }
    }

    companion object {
        private const val DOMAIN_PREF = "Домен"
        private const val DEFAULT_DOMAIN_PREF = "pref_default_domain"
        private const val DOMAIN_TITLE = "Домен"
        private val USER_HASH_REGEX = "user_hash.+'(.+)'".toRegex()
    }
}
