package eu.kanade.tachiyomi.extension.zh.bilimanga

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference

const val PREF_POPULAR_MANGA_DISPLAY = "POPULAR_MANGA_DISPLAY"
const val PREF_RATE_LIMIT = "RATE_LIMIT"

val RATE_LIMIT_REGEX = Regex("^\\d+/\\d+$")

fun preferencesInternal(context: Context, pref: SharedPreferences) = arrayOf(
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
    EditTextPreference(context).apply {
        key = PREF_RATE_LIMIT
        title = "請求速率限制"
        summary = pref.getString(key, "10/10")!!.split("/")
            .let { "每 ${it[1]} 秒内允許 ${it[0]} 个請求通過" }
        dialogMessage = "請按照 */* 的格式輸入，例如 10/2 表示每 2 秒內允許 10 個請求通過，預設值為 10/10"
        setDefaultValue("10/10")
        setOnPreferenceChangeListener { _, newValue ->
            if (RATE_LIMIT_REGEX.matches(newValue as String)) {
                val split = newValue.split("/")
                summary = "每 ${split[1]} 秒内允許 ${split[0]} 个請求通過"
                Toast.makeText(context, "重啟應用後生效", Toast.LENGTH_LONG).show()
                true
            } else {
                Toast.makeText(context, "格式錯誤！請檢查輸入", Toast.LENGTH_LONG).show()
                false
            }
        }
    },
)
