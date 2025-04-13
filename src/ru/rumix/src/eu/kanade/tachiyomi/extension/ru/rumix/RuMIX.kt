package eu.kanade.tachiyomi.extension.ru.rumix

import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.getPreferences
import okhttp3.Request

class RuMIX : GroupLe("RuMIX", "https://rumix.me", "ru") {

    private val preferences = getPreferences()

    override val baseUrl by lazy { getPrefBaseUrl() }

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
                is FilList -> filter.state.forEach { fils ->
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
                else -> return@forEach
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
        arrayOf("По популярности", "Популярно сейчас", "По году", "По алфавиту", "Новинки", "По дате обновления", "По рейтингу"),
    )

    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)
    private class Category(categories: List<Genre>) : Filter.Group<Genre>("Категории", categories)
    private class AgeList(ages: List<Genre>) : Filter.Group<Genre>("Возрастная рекомендация", ages)
    private class FilList(fils: List<Genre>) : Filter.Group<Genre>("Фильтры", fils)

    override fun getFilterList() = FilterList(
        OrderBy(),
        Category(getCategoryList()),
        GenreList(getGenreList()),
        AgeList(getAgeList()),
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
        Genre("Онгоинг", "s_ongoing"),
    )

    private fun getAgeList() = listOf(
        Genre("0+", "el_9154"),
        Genre("6+", "el_9155"),
        Genre("12+", "el_9156"),
        Genre("16+", "el_9139"),
        Genre("18+", "el_9145"),
        Genre("G", "el_6180"),
        Genre("PG", "el_6179"),
        Genre("PG-13", "el_6181"),
    )

    private fun getCategoryList() = listOf(
        Genre("BD", "el_9142"),
        Genre("В цвете", "el_7290"),
        Genre("Веб", "el_2160"),
        Genre("Ёнкома", "el_2161"),
        Genre("Комикс", "el_3515"),
        Genre("Манхва", "el_3001"),
        Genre("Маньхуа", "el_3002"),
        Genre("Ранобэ", "el_8575"),
        Genre("Сборник", "el_2157"),
    )

    private fun getGenreList() = listOf(
        Genre("арт", "el_5685"),
        Genre("боевик", "el_2155"),
        Genre("боевые искусства", "el_2143"),
        Genre("вампиры", "el_2148"),
        Genre("вестерн", "el_9150"),
        Genre("гарем", "el_2142"),
        Genre("гендерная интрига", "el_2156"),
        Genre("героическое фэнтези", "el_2146"),
        Genre("детектив", "el_2152"),
        Genre("дзёсэй", "el_2158"),
        Genre("драма", "el_2118"),
        Genre("игра", "el_2154"),
        Genre("история", "el_2119"),
        Genre("киберпанк", "el_8032"),
        Genre("кодомо", "el_2137"),
        Genre("комедия", "el_2136"),
        Genre("махо-сёдзё", "el_2147"),
        Genre("меха", "el_2126"),
        Genre("мистика", "el_9151"),
        Genre("научная фантастика", "el_2133"),
        Genre("повседневность", "el_2135"),
        Genre("постапокалиптика", "el_2151"),
        Genre("приключения", "el_2130"),
        Genre("психология", "el_2144"),
        Genre("романтика", "el_2121"),
        Genre("самурайский боевик", "el_2124"),
        Genre("сверхъестественное", "el_2159"),
        Genre("сёдзё", "el_2122"),
        Genre("сёнэн", "el_2134"),
        Genre("спорт", "el_2129"),
        Genre("сэйнэн", "el_2138"),
        Genre("трагедия", "el_2153"),
        Genre("триллер", "el_2150"),
        Genre("ужасы", "el_2125"),
        Genre("фантастика", "el_9153"),
        Genre("фэнтези", "el_2131"),
        Genre("школа", "el_2127"),
        Genre("этти", "el_2149"),
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
    }
}
