package eu.kanade.tachiyomi.extension.zh.komiic

import android.content.Context
import android.widget.Toast
import androidx.preference.ListPreference

const val BASE_URL_PREF = "BASE_URL"
const val CHAPTER_FILTER_PREF = "CHAPTER_FILTER"

val mirrorUrls = arrayOf("https://komiic.com", "https://komiic.cc")

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = BASE_URL_PREF
        title = "鏡像站點"
        summary = "註：各站點之間不共享登錄狀態"
        entries = Array(mirrorUrls.size) { mirrorUrls[it].substringAfter("//") }
        entryValues = Array(mirrorUrls.size) { it.toString() }
        setDefaultValue("0")
        setOnPreferenceChangeListener { _, _ ->
            Toast.makeText(context, "重啟生效", Toast.LENGTH_SHORT).show()
            true
        }
    },
    ListPreference(context).apply {
        key = CHAPTER_FILTER_PREF
        title = "章節列表顯示"
        summary = "%s"
        entries = arrayOf("同時顯示卷和章節", "僅顯示章節", "僅顯示卷")
        entryValues = arrayOf("all", "chapter", "book")
        setDefaultValue("all")
    },
)
