package eu.kanade.tachiyomi.extension.zh.bilimanga

import android.content.Context
import androidx.preference.ListPreference

const val PREF_POPULAR_MANGA_DISPLAY = "POPULAR_MANGA_DISPLAY"

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = PREF_POPULAR_MANGA_DISPLAY
        title = "熱門漫畫顯示内容"
        summary = "%s"
        entries = arrayOf(
            "月點擊榜",
            "周點擊榜",
            "月推薦榜",
            "周推薦榜",
            "月鮮花榜",
            "周鮮花榜",
            "月雞蛋榜",
            "周雞蛋榜",
            "最新入庫",
            "收藏榜",
            "新書榜",
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
