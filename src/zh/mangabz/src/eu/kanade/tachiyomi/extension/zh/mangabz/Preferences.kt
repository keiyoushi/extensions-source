package eu.kanade.tachiyomi.extension.zh.mangabz

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.SwitchPreferenceCompat

fun getPreferencesInternal(context: Context) = arrayOf(
    SwitchPreferenceCompat(context).apply {
        key = ZH_HANT_PREF
        title = "使用繁体中文"
        summary = "重启生效，已添加的漫画需要迁移才能更新标题"
        setDefaultValue(false)
    },
)

val SharedPreferences.lang get() = if (getBoolean(ZH_HANT_PREF, false)) "1" else "2"

// Legacy preferences:
// "mainSiteRatelimitPreference" -> 1..10 default "5"
// "imgCDNRatelimitPreference" -> 1..10 default "5"

const val ZH_HANT_PREF = "showZhHantWebsite"

val MIRRORS
    get() = arrayOf(
        Mirror("mangabz.com", "bz/", "mangabz_lang"),
        Mirror("xmanhua.com", "xm/", "xmanhua_lang"),
        Mirror("yymanhua.com", "yy/", "yymanhua_lang"),
    )

class Mirror(
    val domain: String,
    val urlSuffix: String,
    val langCookie: String,
)
