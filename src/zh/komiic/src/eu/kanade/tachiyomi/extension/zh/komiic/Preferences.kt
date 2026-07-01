package eu.kanade.tachiyomi.extension.zh.komiic

import android.content.Context
import androidx.preference.ListPreference

const val CHAPTER_FILTER_PREF = "CHAPTER_FILTER"

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = CHAPTER_FILTER_PREF
        title = "章節列表顯示"
        summary = "%s"
        entries = arrayOf("同時顯示卷和章節", "僅顯示章節", "僅顯示卷")
        entryValues = arrayOf("all", "chapter", "book")
        setDefaultValue("all")
    },
)
