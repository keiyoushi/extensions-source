package eu.kanade.tachiyomi.multisrc.colamanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val uriPart = vals[state].second

        if (uriPart.isNotEmpty()) {
            builder.addQueryParameter(param, uriPart)
        }
    }
}

class SearchTypeFilter(intl: ColaMangaIntl) : UriPartFilter(
    intl.searchType,
    "type",
    arrayOf(
        intl.searchTypeFuzzy to "1",
        intl.searchTypeExact to "2",
    ),
)
