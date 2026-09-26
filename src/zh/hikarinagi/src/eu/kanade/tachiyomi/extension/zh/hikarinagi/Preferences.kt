package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat

object Preferences {

    private const val PREF_READABLE_ONLY = "READABLE_ONLY"
    private const val PREF_DARK_MODE = "DARK_MODE"

    private const val DARK_APP = "app"
    private const val DARK_ALWAYS = "always"
    private const val DARK_NEVER = "never"

    /** The manga section has nothing to configure; the light novel one renders its own pages. */
    fun buildPreferences(context: Context, isNovel: Boolean): List<Preference> = if (!isNovel) {
        emptyList()
    } else {
        listOf(
            ListPreference(context).apply {
                key = PREF_DARK_MODE
                title = "深色模式"
                summary = "%s"
                entries = arrayOf("跟随 Mihon", "始终开启", "始终关闭")
                entryValues = arrayOf(DARK_APP, DARK_ALWAYS, DARK_NEVER)
                setDefaultValue(DARK_APP)
                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(context, "已加载章节需清除缓存后生效", Toast.LENGTH_LONG).show()
                    true
                }
            },
            SwitchPreferenceCompat(context).apply {
                key = PREF_READABLE_ONLY
                title = "不显示无可读章节的轻小说"
                summary = "浏览和搜索时隐藏没有任何可读章节的作品"
                setDefaultValue(true)
            },
        )
    }

    fun isReadableOnly(preferences: SharedPreferences) = preferences.getBoolean(PREF_READABLE_ONLY, true)

    /** Whether a rendered page should be dark; "跟随 Mihon" falls back to the system. */
    fun isDark(preferences: SharedPreferences, appDark: Boolean?, systemDark: Boolean) = when (preferences.getString(PREF_DARK_MODE, DARK_APP)) {
        DARK_ALWAYS -> true
        DARK_NEVER -> false
        else -> appDark ?: systemDark
    }
}
