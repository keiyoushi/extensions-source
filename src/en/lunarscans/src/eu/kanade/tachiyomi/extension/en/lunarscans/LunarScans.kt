package eu.kanade.tachiyomi.extension.en.lunarscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.getPreferences

class LunarScans :
    MangaThemesia(
        "Lunar Scans",
        "https://lunarscan.org",
        "en",
        "/series",
    ),
    ConfigurableSource {

    private val preferences = getPreferences()

    override val client = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(1)
        .build()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Note: Can't be used with text search!"),
            Filter.Separator(),
            StatusFilter(intl["status_filter_title"], statusOptions),
            TypeFilter(intl["type_filter_title"], typeFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )
        if (!genrelist.isNullOrEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreListFilter(intl["genre_filter_title"], getGenreList()),
                ),
            )
        } else {
            filters.add(
                Filter.Header(intl["genre_missing_warning"]),
            )
        }
        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header(intl["project_filter_warning"]),
                    Filter.Header(intl.format("project_filter_name", name)),
                    ProjectFilter(intl["project_filter_title"], projectFilterOptions),
                ),
            )
        }
        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }
}
