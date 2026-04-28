package eu.kanade.tachiyomi.extension.all.myreadingmanga

import android.net.Uri
import eu.kanade.tachiyomi.source.model.Filter

internal class EnforceLanguageFilter(val siteLang: String) :
    Filter.CheckBox("Enforce language", true),
    UriFilter {
    override fun addToUri(uri: Uri.Builder) {
        if (state) uri.appendQueryParameter("ep_filter_lang", siteLang)
    }
}

internal class GenreFilter(genres: Array<Pair<String, String>>) : UriSelectFilter("Genre", "ep_filter_genre", arrayOf(Pair("Any", ""), *genres))
internal class TagFilter(popTags: Array<Pair<String, String>>) : UriSelectFilter("Popular Tags", "ep_filter_post_tag", arrayOf(Pair("Any", ""), *popTags))
internal class CatFilter(catIds: Array<Pair<String, String>>) : UriSelectFilter("Categories", "ep_filter_category", arrayOf(Pair("Any", ""), *catIds))
internal class PairingFilter(pairs: Array<Pair<String, String>>) : UriSelectFilter("Pairing", "ep_filter_pairing", arrayOf(Pair("Any", ""), *pairs))
internal class ScanGroupFilter(groups: Array<Pair<String, String>>) : UriSelectFilter("Scanlation Group", "ep_filter_group", arrayOf(Pair("Any", ""), *groups))
internal class SearchSortTypeList : Filter.Select<String>("Sort by", arrayOf("Newest", "Oldest", "Random", "More relevant"))

/**
 * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
 * If an entry is selected it is appended as a query parameter onto the end of the URI.
 * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the URI.
 */
internal open class UriSelectFilter(
    displayName: String,
    val uriParam: String,
    val vals: Array<Pair<String, String>>,
    val firstIsUnspecified: Boolean = true,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue),
    UriFilter {
    override fun addToUri(uri: Uri.Builder) {
        if (state != 0 || !firstIsUnspecified) {
            uri.appendQueryParameter(uriParam, vals[state].second)
        }
    }
}

/**
 * Represents a filter that is able to modify a URI.
 */
internal interface UriFilter {
    fun addToUri(uri: Uri.Builder)
}
