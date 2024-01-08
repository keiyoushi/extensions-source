package eu.kanade.tachiyomi.extension.zh.mangabz

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat

fun getPreferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        val mirrors = MIRRORS
        val size = mirrors.size

        key = MIRROR_PREF
        title = "镜像站点"
        summary = "%s\n重启生效，不同站点的数据有细微差异"
        entries = Array(size) { mirrors[it].domain }
        entryValues = Array(size) { it.toString() }
        setDefaultValue("0")
    },

    SwitchPreferenceCompat(context).apply {
        key = ZH_HANT_PREF
        title = "使用繁体中文"
        summary = "重启生效，已添加的漫画需要迁移才能更新标题"
        setDefaultValue(false)
    },
)

val SharedPreferences.mirror: Mirror
    get() {
        val mirrors = MIRRORS
        val mirrorPref = getString(MIRROR_PREF, "0")!!
        val mirrorIndex = mirrorPref.toInt().coerceAtMost(mirrors.size - 1)
        return mirrors[mirrorIndex]
    }

val SharedPreferences.lang get() = if (getBoolean(ZH_HANT_PREF, false)) "1" else "2"

// Legacy preferences:
// "mainSiteRatelimitPreference" -> 1..10 default "5"
// "imgCDNRatelimitPreference" -> 1..10 default "5"

const val MIRROR_PREF = "mirror"
const val ZH_HANT_PREF = "showZhHantWebsite"

val MIRRORS
    get() = arrayOf(
        Mirror("mangabz.com", "bz/", "mangabz_lang"),
        Mirror("xmanhua.com", "xm/", "xmanhua_lang"),
    )

class Mirror(
    val domain: String,
    val urlSuffix: String,
    val langCookie: String,
)
