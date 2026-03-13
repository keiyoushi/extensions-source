package eu.kanade.tachiyomi.extension.zh.noyacg

import android.content.Context
import androidx.preference.ListPreference

const val POPULAR_MANGAS_PREF = "POPULAR_MANGAS"
const val ADULT_PREF = "ADULT"

fun getPreferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = POPULAR_MANGAS_PREF
        title = "热门漫画显示内容"
        summary = "%s"
        setDefaultValue("day")
        entries = arrayOf("日阅读榜", "周阅读榜", "月阅读榜")
        entryValues = arrayOf("day", "week", "month")
    },
    ListPreference(context).apply {
        key = ADULT_PREF
        title = "漫画内容类型"
        summary = "%s"
        setDefaultValue("both")
        entries = arrayOf("仅显示全年龄内容", "仅显示成人内容", "显示所有内容")
        entryValues = arrayOf("false", "true", "both")
    },
)
