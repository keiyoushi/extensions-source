package eu.kanade.tachiyomi.extension.zh.favcomic

import android.content.Context
import androidx.preference.ListPreference

const val PREF_BASE_URL = "BASE_URL"
const val PREF_RANK_TYPE = "RANK_TYPE"
const val PREF_MANGA_TYPE = "MANGA_TYPE"

val mirrorUrls = arrayOf(
    "https://www.favcomic.com",
    "https://www.favcomic.xyz",
    "https://www.favcomic.net",
    "https://www.favcomic.cc",
)

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = PREF_BASE_URL
        title = "镜像站点"
        summary = "注：各站点之间不共享登录状态"
        entries = Array(mirrorUrls.size) { mirrorUrls[it].substringAfter("//") }
        entryValues = Array(mirrorUrls.size) { it.toString() }
        setDefaultValue("0")
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
        summary = "指定“热门”和“最近更新”显示的漫画类型（“热门”没有“性感图库”类型，选择该项会显示全部类型排行）"
        entries = arrayOf("少男漫画", "少女漫画", "性感图库", "成人漫画")
        entryValues = arrayOf("boy-1", "girl-2", "picture-3", "r18-4")
        setDefaultValue("boy-1")
    },
)
