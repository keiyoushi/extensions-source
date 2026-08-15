package eu.kanade.tachiyomi.extension.zh.noyacg

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference

const val POPULAR_MANGAS_PREF = "POPULAR_MANGAS"
const val ADULT_PREF = "ADULT"
const val USERNAME_PREF = "USERNAME"
const val PASSWORD_PREF = "PASSWORD"

fun getPreferencesInternal(context: Context, preferences: SharedPreferences) = arrayOf(
    EditTextPreference(context).apply {
        key = USERNAME_PREF
        title = "用戶名稱 / 電郵"
        summary = preferences.getString(key, "")?.takeIf(String::isNotEmpty) ?: "未設定"
        dialogTitle = title
        setOnPreferenceChangeListener { _, newValue ->
            summary = (newValue as String).takeIf(String::isNotEmpty) ?: "未設定"
            true
        }
    },
    EditTextPreference(context).apply {
        key = PASSWORD_PREF
        title = "密碼"
        summary = if (preferences.getString(key, "").isNullOrEmpty()) "未設定" else "********"
        dialogTitle = title
        setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        setOnPreferenceChangeListener { _, newValue ->
            summary = if ((newValue as String).isEmpty()) "未設定" else "********"
            true
        }
    },
    ListPreference(context).apply {
        key = POPULAR_MANGAS_PREF
        title = "熱門漫畫顯示內容"
        summary = "%s"
        setDefaultValue("day")
        entries = arrayOf("日閱讀榜", "周閱讀榜", "月閱讀榜")
        entryValues = arrayOf("day", "week", "month")
    },
    ListPreference(context).apply {
        key = ADULT_PREF
        title = "漫畫內容類型"
        summary = "%s"
        setDefaultValue("both")
        entries = arrayOf("僅顯示全年齡內容", "僅顯示成人内容", "顯示所有内容")
        entryValues = arrayOf("false", "true", "both")
    },
)
