package eu.kanade.tachiyomi.extension.ru.usagi

import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.getPreferences

class Usagi : GroupLe("Usagi", "https://web.usagi.one/", "ru") {

    private val preferences = getPreferences()

    override val baseUrl by lazy { getPrefBaseUrl() }

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        GenreList(getGenreList()),
        AgeList(getAgeList()),
        MoreList(getMoreList()),
        AdditionalFilterList(getAdditionalFilterList()),
    )
    private fun getAdditionalFilterList() = listOf(
        Genre("Высокий рейтинг", "s_high_rate"),
        Genre("Сингл", "s_single"),
        Genre("Для взрослых", "s_mature"),
        Genre("Завершенная", "s_completed"),
        Genre("Переведено", "s_translated"),
        Genre("Заброшен перевод", "s_abandoned_popular"),
        Genre("Длинная", "s_many_chapters"),
        Genre("Ожидает загрузки", "s_wait_upload"),
        Genre("Белые жанры", "s_not_pessimized"),
        Genre("Онгоинг", "s_ongoing"),
    )
    private fun getMoreList() = listOf(
        Genre("В цвете", "el_7290"),
        Genre("Веб", "el_2160"),
        Genre("На экранах", "el_9795"),
        Genre("Сборник", "el_2157"),
    )

    private fun getAgeList() = listOf(
        Genre("G", "el_6180"),
        Genre("NC-17", "el_9848"),
        Genre("PG", "el_6179"),
        Genre("PG-13", "el_6181"),
        Genre("R", "el_9847"),
    )

    private fun getCategoryList() = listOf(
        Genre("OEL-манга", "el_9577"),
        Genre("Арт", "el_5685"),
        Genre("Додзинси", "el_2141"),
        Genre("Ёнкома", "el_2161"),
        Genre("Комикс", "el_3515"),
        Genre("Манга", "el_9451"),
        Genre("Манхва", "el_3001"),
        Genre("Маньхуа", "el_3002"),
    )

    private fun getGenreList() = listOf(
        Genre("боевик", "el_2155"),
        Genre("боевые искусства", "el_2143"),
        Genre("гарем", "el_2142"),
        Genre("гендерная интрига", "el_2156"),
        Genre("детектив", "el_2152"),
        Genre("дзёсэй", "el_2158"),
        Genre("драма", "el_2118"),
        Genre("история", "el_2119"),
        Genre("исэкай", "el_9450"),
        Genre("киберпанк", "el_8032"),
        Genre("кодомо", "el_2137"),
        Genre("комедия", "el_2136"),
        Genre("музыка", "el_9514"),
        Genre("научная фантастика", "el_2133"),
        Genre("пародия", "el_9524"),
        Genre("повседневность", "el_2135"),
        Genre("постапокалиптика", "el_2151"),
        Genre("приключения", "el_2130"),
        Genre("психология", "el_2144"),
        Genre("романтика", "el_2121"),
        Genre("сверхъестественное", "el_2159"),
        Genre("сёдзё", "el_2122"),
        Genre("сёнэн", "el_2134"),
        Genre("спорт", "el_2129"),
        Genre("сэйнэн", "el_2138"),
        Genre("трагедия", "el_2153"),
        Genre("триллер", "el_2150"),
        Genre("ужасы", "el_2125"),
        Genre("уся", "el_9560"),
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
        private const val DOMAIN_PREF = "pref_domain"
        private const val DEFAULT_DOMAIN_PREF = "pref_default_domain"
        private const val DOMAIN_TITLE = "Домен"
    }
}
