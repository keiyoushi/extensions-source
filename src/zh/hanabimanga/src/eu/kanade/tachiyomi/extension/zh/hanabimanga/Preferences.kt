package eu.kanade.tachiyomi.extension.zh.hanabimanga

import android.content.Context
import android.content.SharedPreferences
import android.util.Patterns
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat

const val PREF_EMAIL = "EMAIL"
const val PREF_PASSWORD = "PASSWORD"
const val PREF_POPULAR_MANGA = "POPULAR_MANGA"
const val PREF_AI_SR = "AI_SUPER_RESOLUTION"

fun preferencesInternal(context: Context, pref: SharedPreferences) = arrayOf(
    EditTextPreference(context).apply {
        key = PREF_EMAIL
        title = "登录邮箱"
        summary = pref.getString(key, "未设置")
        setDefaultValue(null)
        setOnPreferenceChangeListener { _, newValue ->
            if (Patterns.EMAIL_ADDRESS.matcher(newValue as String).matches()) {
                summary = newValue
                true
            } else {
                Toast.makeText(context, "邮箱格式不正确！", Toast.LENGTH_SHORT).show()
                false
            }
        }
    },
    EditTextPreference(context).apply {
        key = PREF_PASSWORD
        title = "登录密码"
        summary = pref.getString(key, null)?.let { "*".repeat(it.length) } ?: "未设置"
        setDefaultValue(null)
        setOnPreferenceChangeListener { _, newValue ->
            summary = "*".repeat((newValue as String).length)
            true
        }
    },
    ListPreference(context).apply {
        key = PREF_POPULAR_MANGA
        title = "热门漫画显示"
        summary = "%s"
        entries = arrayOf("日榜", "周榜", "月榜", "评分", "人气")
        entryValues = arrayOf(
            "popularity_daily.desc.nullslast",
            "popularity_weekly.desc.nullslast",
            "popularity_monthly.desc.nullslast",
            "rating_average.desc.nullslast",
            "rating_count.desc.nullslast",
        )
        setDefaultValue("popularity_daily.desc.nullslast")
    },
    SwitchPreferenceCompat(context).apply {
        key = PREF_AI_SR
        title = "AI 超分辨率"
        summary = "尝试获取超分辨率图片，普通用户每日限 5 个章节。不建议非 VIP 用户开启，避免额度用完后还要再关"
        setDefaultValue(false)
    },
)
