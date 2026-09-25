package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat

object Preferences {

    private const val PREF_CATEGORY = "CATEGORY"
    private const val PREF_READABLE_ONLY = "READABLE_ONLY"

    private const val CATEGORY_MANGA = "manga"
    private const val CATEGORY_NOVEL = "novel"

    /**
     * The light novel section shares the numeric id space with the manga section,
     * so novel entries are prefixed to keep both from colliding in the library.
     */
    const val NOVEL_URL_PREFIX = "novel/"

    fun buildPreferences(context: Context, isNovel: Boolean) = listOf(
        ListPreference(context).apply {
            key = PREF_CATEGORY
            title = "内容分类"
            summary = "%s"
            entries = arrayOf("漫画", "轻小说（条漫）")
            entryValues = arrayOf(CATEGORY_MANGA, CATEGORY_NOVEL)
            setDefaultValue(CATEGORY_MANGA)
        },
        SwitchPreferenceCompat(context).apply {
            key = PREF_READABLE_ONLY
            title = "不显示无可读章节的轻小说"
            summary = "浏览和搜索时隐藏没有任何可读章节的作品"
            setDefaultValue(false)
            setEnabled(isNovel)
        },
    )

    fun isNovel(preferences: SharedPreferences) = preferences.getString(PREF_CATEGORY, CATEGORY_MANGA) == CATEGORY_NOVEL

    fun isReadableOnly(preferences: SharedPreferences) = preferences.getBoolean(PREF_READABLE_ONLY, false)
}
