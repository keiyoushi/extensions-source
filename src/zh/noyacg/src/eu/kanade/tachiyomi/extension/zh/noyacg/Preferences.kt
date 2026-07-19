package eu.kanade.tachiyomi.extension.zh.noyacg

import android.content.Context
import android.widget.Toast
import androidx.preference.ListPreference

const val POPULAR_MANGAS_PREF = "POPULAR_MANGAS"
const val ADULT_PREF = "ADULT"
const val IMG_HOSTING_PREF = "IMG_HOSTING"

val imgBaseUrls = arrayOf("https://img.noymanga.com", "https://img.noyteam.online", "https://img.noy.asia")

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
        setOnPreferenceChangeListener { _, _ ->
            Toast.makeText(context, "重啟應用後生效", Toast.LENGTH_SHORT).show()
            true
        }
    },
    ListPreference(context).apply {
        key = IMG_HOSTING_PREF
        title = "切換伺服器分流（圖床）"
        summary = "%s"
        setDefaultValue("https://img.noymanga.com")
        entries = imgBaseUrls.map { it.removePrefix("https://") }.toTypedArray()
        entryValues = imgBaseUrls
    },
)
