package eu.kanade.tachiyomi.extension.zh.noyacg

import android.content.Context
import androidx.preference.ListPreference

const val POPULAR_MANGAS_PREF = "POPULAR_MANGAS"
const val ADULT_PREF = "ADULT"

fun getPreferencesInternal(context: Context) = arrayOf(
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
