package eu.kanade.tachiyomi.extension.zh.komiic

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat

const val CHAPTER_FILTER_PREF = "CHAPTER_FILTER"
const val CHECK_API_LIMIT_PREF = "CHECK_API_LIMIT"

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = CHAPTER_FILTER_PREF
        title = "章节列表显示"
        summary = "注：部分漫画小概率会有章节缺失，仅显示章节就没法通过看卷的内容来补充了。建议不要仅显示卷，会导致无法获取及时章节更新"
        entries = arrayOf("同时显示卷和章节", "仅显示章节", "仅显示卷")
        entryValues = arrayOf("all", "chapter", "book")
        setDefaultValue("all")
    },
    SwitchPreferenceCompat(context).apply {
        key = CHECK_API_LIMIT_PREF
        title = "自动检查API是否受限"
        summary = "每次点击单个章节请求漫画图片时，会自动检查一次图片API是否达到今日请求上限。若已达上限，则终止后续操作"
        setDefaultValue(true)
    },
)
