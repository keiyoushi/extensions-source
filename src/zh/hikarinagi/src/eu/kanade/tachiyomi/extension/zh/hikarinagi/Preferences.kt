package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat

object Preferences {

    private const val PREF_CATEGORY = "Hikarinagi::CATEGORY"
    private const val PREF_READABLE_ONLY = "Hikarinagi::READABLE_ONLY"

    private const val CATEGORY_MANGA = "manga"
    private const val CATEGORY_NOVEL = "novel"

    /**
     * The light novel section shares the numeric id space with the manga section,
     * so novel entries are prefixed to keep both from colliding in the library.
     */
    const val NOVEL_URL_PREFIX = "novel/"

    fun buildPreferences(context: Context, isNovel: Boolean): List<Preference> = listOf(
        ListPreference(context).apply {
            key = PREF_CATEGORY
            title = "内容分类"
            summary = "%s"
            entries = arrayOf("漫画", "轻小说")
            entryValues = arrayOf(CATEGORY_MANGA, CATEGORY_NOVEL)
            setDefaultValue(CATEGORY_MANGA)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(context, "已切换分类，请重新进入浏览/搜索以刷新列表和筛选", Toast.LENGTH_LONG).show()
                true
            }
        },
        SwitchPreferenceCompat(context).apply {
            key = PREF_READABLE_ONLY
            title = "仅显示有正文的轻小说"
            summary = "浏览和搜索时不显示没有在线正文（EPUB）的作品\n只在「内容分类」为轻小说时可用"
            setDefaultValue(false)
            setEnabled(isNovel)
        },
    )

    fun isNovel(preferences: SharedPreferences): Boolean = preferences.getString(PREF_CATEGORY, CATEGORY_MANGA) == CATEGORY_NOVEL

    fun isReadableOnly(preferences: SharedPreferences): Boolean = preferences.getBoolean(PREF_READABLE_ONLY, false)
}
