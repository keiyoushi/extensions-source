package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.EditTextPreference

internal fun getPreferenceList(context: Context) = arrayOf(
    EditTextPreference(context).apply {
        key = BLOCK_PREF
        title = "屏蔽词列表"
        setDefaultValue(
            "// 例如 \"YAOI cos 扶他 毛絨絨 獵奇 韩漫 韓漫\", " +
                "关键词之间用空格分离, 大小写不敏感, \"//\"后的字符会被忽略",
        )
        dialogTitle = "关键词列表"
    },
)

internal const val BLOCK_PREF = "BLOCK_GENRES_LIST"

internal val SharedPreferences.blockList: List<String>
    get() = getString(BLOCK_PREF, "")!!.substringBefore("//").trim().lowercase().split(' ')
