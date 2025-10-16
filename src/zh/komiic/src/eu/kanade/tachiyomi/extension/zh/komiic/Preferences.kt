package eu.kanade.tachiyomi.extension.zh.komiic

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat

const val CHAPTER_FILTER_PREF = "CHAPTER_FILTER"
const val CHECK_API_LIMIT_PREF = "CHECK_API_LIMIT"

fun preferencesInternal(context: Context) = arrayOf(
    ListPreference(context).apply {
        key = CHAPTER_FILTER_PREF
        title = "章節列表顯示"
        summary = "注：部分漫畫小概率會有章節缺失，僅顯示章節就沒法通過看卷的內容來補充了。建議不要僅顯示卷，會導致無法獲取及時章節更新"
        entries = arrayOf("同時顯示卷和章節", "僅顯示章節", "僅顯示卷")
        entryValues = arrayOf("all", "chapter", "book")
        setDefaultValue("all")
    },
    SwitchPreferenceCompat(context).apply {
        key = CHECK_API_LIMIT_PREF
        title = "自動檢查 API 狀態"
        summary = "點擊單個章節請求漫畫圖片時，自動檢查一次圖片API是否達到今日請求上限。若已達上限，則終止後續操作（注：關閉后仍會檢查API，只是不再終止操作）"
        setDefaultValue(true)
    },
)
