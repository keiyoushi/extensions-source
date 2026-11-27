package eu.kanade.tachiyomi.extension.zh.bilimanga

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.SwitchPreferenceCompat

const val PREF_POPULAR_MANGA_DISPLAY = "POPULAR_MANGA_DISPLAY"
const val PREF_DESCRIPTION = "DESCRIPTION"
const val PREF_SOLVE_CAPTCHA = "SOLVE_CAPTCHA"

val DEFAULT_SET = setOf("A", "B", "C")

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
    MultiSelectListPreference(context).apply {
        key = PREF_DESCRIPTION
        title = "作品信息顯示偏好"
        summary = "設定作品簡介中需要顯示的額外信息"
        dialogTitle = "勾選需要顯示的信息"
        entries = arrayOf("作品公告", "作品别名", "跳轉連結")
        entryValues = arrayOf("A", "B", "C")
        setDefaultValue(DEFAULT_SET)
    },
    SwitchPreferenceCompat(context).apply {
        key = PREF_SOLVE_CAPTCHA
        title = "自動輸入搜尋驗證碼"
        summary = "搜尋需要驗證碼時自動識別並提交；關閉時需在 WebView 中手動驗證"
        setDefaultValue(false)
    },
)
