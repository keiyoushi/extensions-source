package eu.kanade.tachiyomi.extension.ru.rumix

import android.app.Application
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RuMIX : GroupLe("RuMIX", "https://rumix.me", "ru") {

    private val preferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val baseUrl by lazy { getPrefBaseUrl() }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = super.searchMangaRequest(page, query, filters).url.newBuilder()
        (if (filters.isEmpty()) getFilterList().reversed() else filters.reversed()).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    if (url.toString().contains("&") && filter.state < 6) {
                        url.addQueryParameter("sortType", arrayOf("RATING", "POPULARITY", "YEAR", "NAME", "DATE_CREATE", "DATE_UPDATE")[filter.state])
                    } else {
                        val ord = arrayOf("rate", "popularity", "year", "name", "created", "updated", "votes")[filter.state]
                        return GET("$baseUrl/list?sortType=$ord&offset=${70 * (page - 1)}", headers)
                    }
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
        arrayOf("По популярности", "Популярно сейчас", "По году", "По имени", "Новинки", "По дате обновления", "По рейтингу"),
    )

    override fun getFilterList() = FilterList(
        OrderBy(),
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
