package eu.kanade.tachiyomi.extension.zh.wnacg

import android.content.SharedPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString

var SharedPreferences.extraCategories: List<Pair<String, String>>
    get() = getString(EXTRA_CATEGORIES_PREF, null)?.parseAs<List<Pair<String, String>>>().orEmpty()
    set(value) = edit().putString(EXTRA_CATEGORIES_PREF, value.toJsonString()).apply()

fun SharedPreferences.preferenceMigration() {
    if (contains(URL_LIST_PREF)) {
        edit()
            .remove(DEFAULT_LIST_PREF)
            .remove(URL_LIST_PREF)
            .remove(URL_INDEX_PREF)
            .apply()
    }
}

private const val DEFAULT_LIST_PREF = "defaultBaseUrl"
private const val URL_LIST_PREF = "baseUrlList"
private const val URL_INDEX_PREF = "baseUrlIndex"
private const val EXTRA_CATEGORIES_PREF = "extraCategories"
