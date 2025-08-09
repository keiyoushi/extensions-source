package eu.kanade.tachiyomi.extension.zh.bilinovel

import android.content.Context
import androidx.preference.ListPreference

const val PREF_POPULAR_MANGA_DISPLAY = "POPULAR_MANGA_DISPLAY"

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = PREF_POPULAR_MANGA_DISPLAY
        title = "热门漫画显示内容"
        summary = "%s"
        entries = arrayOf(
            "月点击榜",
            "周点击榜",
            "月推荐榜",
            "周推荐榜",
            "月鲜花榜",
            "周鲜花榜",
            "月鸡蛋榜",
            "周鸡蛋榜",
            "最新入库",
            "收藏榜",
            "新书榜",
        )
        entryValues = arrayOf(
            "/top/monthvisit/%d.html",
            "/top/weekvisit/%d.html",
            "/top/monthvote/%d.html",
            "/top/weekvote/%d.html",
            "/top/monthflower/%d.html",
            "/top/weekflower/%d.html",
            "/top/monthegg/%d.html",
            "/top/weekegg/%d.html",
            "/top/postdate/%d.html",
            "/top/goodnum/%d.html",
            "/top/newhot/%d.html",
        )
        setDefaultValue("/top/weekvisit/%d.html")
    },
)
