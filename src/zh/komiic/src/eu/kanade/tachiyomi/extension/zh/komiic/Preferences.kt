package eu.kanade.tachiyomi.extension.zh.komiic

import android.content.Context
import androidx.preference.ListPreference

const val CHAPTER_FILTER_PREF = "CHAPTER_FILTER"
const val CHAPTER_SIZE_FORMAT_PREF = "CHAPTER_SIZE_FORMAT"

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = CHAPTER_FILTER_PREF
        title = "章节列表显示"
        summary = "注意：部分漫画小概率会有章节缺失，仅显示章节就不能通过卷的内容来补充了。建议不要仅显示卷，会导致无法获取及时章节更新"
        entries = arrayOf("同时显示卷和章节", "仅显示章节", "仅显示卷")
        entryValues = arrayOf("all", "chapter", "book")
        setDefaultValue("all")
    },
    ListPreference(context).apply {
        key = CHAPTER_SIZE_FORMAT_PREF
        title = "章节页数格式"
        summary = "%s"
        entries = arrayOf("120p", "120P", "共120页")
        entryValues = arrayOf("%dp", "%dP", "共%d页")
        setDefaultValue("%dP")
    },
)
