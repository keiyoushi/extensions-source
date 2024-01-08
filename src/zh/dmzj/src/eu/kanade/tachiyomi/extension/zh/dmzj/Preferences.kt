package eu.kanade.tachiyomi.extension.zh.dmzj

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat

// Legacy preferences:
// "apiRatelimitPreference" -> 1..10 default "5"
// "imgCDNRatelimitPreference" -> 1..10 default "5"
// "licensedList" -> StringSet of manga ID
// "hiddenList" -> StringSet of manga ID

fun getPreferencesInternal(context: Context) = arrayOf(

    ListPreference(context).apply {
        key = IMAGE_QUALITY_PREF
        title = "图片质量"
        summary = "%s\n修改后，已加载的章节需要清除章节缓存才能生效。"
        entries = arrayOf("优先原图", "只用原图 (加载出错概率更高)", "优先低清")
        entryValues = arrayOf(AUTO_RES, ORIGINAL_RES, LOW_RES)
        setDefaultValue(AUTO_RES)
    },

    SwitchPreferenceCompat(context).apply {
        key = CHAPTER_COMMENTS_PREF
        title = "章末吐槽页"
        summary = "修改后，已加载的章节需要清除章节缓存才能生效。"
        setDefaultValue(false)
    },

    SwitchPreferenceCompat(context).apply {
        key = MULTI_GENRE_FILTER_PREF
        title = "分类筛选时允许勾选多个题材"
        summary = "可以更精细地筛选出同时符合多个题材的作品。"
        setDefaultValue(false)
    },
)

val SharedPreferences.imageQuality get() = getString(IMAGE_QUALITY_PREF, AUTO_RES)!!

val SharedPreferences.showChapterComments get() = getBoolean(CHAPTER_COMMENTS_PREF, false)

val SharedPreferences.isMultiGenreFilter get() = getBoolean(MULTI_GENRE_FILTER_PREF, false)

private const val IMAGE_QUALITY_PREF = "imageSourcePreference"
const val AUTO_RES = "PREFER_ORIG_RES"
const val ORIGINAL_RES = "ORIG_RES_ONLY"
const val LOW_RES = "LOW_RES_ONLY"

private const val CHAPTER_COMMENTS_PREF = "chapterComments"
private const val MULTI_GENRE_FILTER_PREF = "multiGenreFilter"
