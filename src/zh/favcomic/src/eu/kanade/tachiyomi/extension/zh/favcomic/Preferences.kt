package eu.kanade.tachiyomi.extension.zh.favcomic

import android.content.Context
import android.widget.Toast
import androidx.preference.ListPreference

const val PREF_BASE_URL = "BASE_URL"
const val PREF_RANK_TYPE = "RANK_TYPE"
const val PREF_MANGA_TYPE = "MANGA_TYPE"

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = PREF_BASE_URL
        title = "镜像站点"
        summary = "注：各站点之间不共享登录状态"
        entries = arrayOf("favcomic.com（主站）", "favcomic.xyz", "favcomic.net", "favcomic.cc")
        entryValues = arrayOf("https://www.favcomic.com", "https://www.favcomic.xyz", "https://www.favcomic.net", "https://www.favcomic.cc")
        setDefaultValue("https://www.favcomic.com")
        setOnPreferenceChangeListener { _, _ ->
            Toast.makeText(context, "重启后生效", Toast.LENGTH_SHORT).show()
            true
        }
    },
    ListPreference(context).apply {
        key = PREF_RANK_TYPE
        title = "排行榜（热门）"
        summary = "%s"
        entries = arrayOf("周排名", "月排名", "总排名")
        entryValues = arrayOf("1", "2", "3")
        setDefaultValue("1")
    },
    ListPreference(context).apply {
        key = PREF_MANGA_TYPE
        title = "漫画类型（最近更新）"
        summary = "%s"
        entries = arrayOf("少男漫画", "少女漫画", "性感图库", "成人漫画")
        entryValues = arrayOf("boy", "girl", "picture", "r18")
        setDefaultValue("boy")
    },
)
