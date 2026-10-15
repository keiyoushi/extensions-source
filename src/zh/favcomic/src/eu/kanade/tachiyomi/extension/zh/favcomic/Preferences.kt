package eu.kanade.tachiyomi.extension.zh.favcomic

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference

const val PREF_RANK_TYPE = "RANK_TYPE"
const val PREF_MANGA_TYPE = "MANGA_TYPE"
const val PREF_USERNAME = "USERNAME"
const val PREF_PASSWORD = "PASSWORD"

fun preferencesInternal(context: Context, preferences: SharedPreferences) = arrayOf(
    EditTextPreference(context).apply {
        key = PREF_USERNAME
        title = "账号 (喜漫注册邮箱)"
        summary = preferences.getString(key, "")?.takeIf(String::isNotEmpty) ?: "未设置"
        dialogTitle = title
        setOnPreferenceChangeListener { _, newValue ->
            summary = (newValue as String).takeIf(String::isNotEmpty) ?: "未设置"
            true
        }
    },
    EditTextPreference(context).apply {
        key = PREF_PASSWORD
        title = "密码"
        summary = if (preferences.getString(key, "").isNullOrEmpty()) "未设置" else "********"
        dialogTitle = title
        setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        setOnPreferenceChangeListener { _, newValue ->
            summary = if ((newValue as String).isEmpty()) "未设置" else "********"
            true
        }
    },
    ListPreference(context).apply {
        key = PREF_RANK_TYPE
        title = "热门排行"
        summary = "%s"
        entries = arrayOf("周排名", "月排名", "总排名")
        entryValues = arrayOf("1", "2", "3")
        setDefaultValue("1")
    },
    ListPreference(context).apply {
        key = PREF_MANGA_TYPE
        title = "漫画类型"
        summary = "指定“热门”和“最近更新”显示的漫画类型\n（“热门”没有“性感图库”类型，选择该项会显示全部类型）"
        entries = arrayOf("少男漫画", "少女漫画", "性感图库", "成人漫画")
        entryValues = arrayOf("boy-1", "girl-2", "picture-3", "r18-4")
        setDefaultValue("boy-1")
    },
)
