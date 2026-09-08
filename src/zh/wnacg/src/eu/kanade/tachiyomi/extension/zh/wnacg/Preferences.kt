package eu.kanade.tachiyomi.extension.zh.wnacg

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.random.Random

private const val DEFAULT_LIST = "https://www.wn07.cfd,https://www.wn07.shop,https://www.wn06.cfd,https://www.wn06.shop"

fun getPreferencesInternal(
    context: Context,
    preferences: SharedPreferences,
    isUrlUpdated: Boolean,
) = arrayOf(
    ListPreference(context).apply {
        key = URL_INDEX_PREF
        title = "网址"
        summary = if (isUrlUpdated) "%s\n网址已自动更新，请重启应用。" else "%s\n正常情况下会自动更新。重启生效。"

        val options = preferences.urlList
        val count = options.size
        entries = options.toTypedArray()
        entryValues = Array(count, Int::toString)
    },

    EditTextPreference(context).apply {
        key = TITLE_BLACKLIST_PREF
        title = "标题屏蔽关键词"
        summary = titleBlacklistSummary(preferences.getString(TITLE_BLACKLIST_PREF, ""))
        setOnPreferenceChangeListener { _, newValue ->
            summary = titleBlacklistSummary(newValue as String)
            true
        }
        dialogTitle = "标题屏蔽关键词"
        setDefaultValue("")
    },

    EditTextPreference(context).apply {
        key = BLACKLIST_MAX_SCAN_PAGES_PREF
        title = "黑名单过滤最大扫描页数"
        summary = maxScanPagesSummary(preferences.blacklistMaxScanPages)
        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
        setOnPreferenceChangeListener { _, newValue ->
            val value = (newValue as String).toIntOrNull()
            if (value == null || value < 1) {
                Toast.makeText(context, "请输入不小于 1 的正整数。", Toast.LENGTH_SHORT).show()
                false
            } else {
                summary = maxScanPagesSummary(value)
                true
            }
        }
        dialogTitle = "黑名单过滤最大扫描页数"
        setDefaultValue(DEFAULT_MAX_SCAN_PAGES.toString())
    },

    SwitchPreferenceCompat(context).apply {
        key = FILTER_SEARCH_RESULTS_PREF
        title = "在搜索结果中使用标题屏蔽"
        summary = "开启后，关键词、分类和标签搜索结果也会应用标题屏蔽关键词。"
        setDefaultValue(true)
    },
)

val SharedPreferences.baseUrl: String
    get() {
        val list = urlList
        return list.getOrNull(urlIndex) ?: list[0]
    }

val SharedPreferences.urlIndex get() = getString(URL_INDEX_PREF, "-1")!!.toInt()
val SharedPreferences.urlList get() = getString(URL_LIST_PREF, DEFAULT_LIST)!!.split(",")

val SharedPreferences.titleBlacklist: List<String>
    get() = getString(TITLE_BLACKLIST_PREF, "")
        .orEmpty()
        .split(',', '\n', '\r')
        .map(String::trim)
        .filter(String::isNotEmpty)

val SharedPreferences.filterSearchResults: Boolean
    get() = getBoolean(FILTER_SEARCH_RESULTS_PREF, true)

val SharedPreferences.blacklistMaxScanPages: Int
    get() = getString(BLACKLIST_MAX_SCAN_PAGES_PREF, DEFAULT_MAX_SCAN_PAGES.toString())
        ?.toIntOrNull()
        ?.takeIf { it >= 1 }
        ?: DEFAULT_MAX_SCAN_PAGES

private fun titleBlacklistSummary(value: String?) = value?.takeIf(String::isNotBlank)?.let { "$it\n$TITLE_BLACKLIST_SUMMARY" }
    ?: TITLE_BLACKLIST_SUMMARY

private fun maxScanPagesSummary(value: Int) = "当前值：$value\n$BLACKLIST_MAX_SCAN_PAGES_SUMMARY"

fun getCiBaseUrl() = DEFAULT_LIST.replace(",", "#, ")

fun SharedPreferences.preferenceMigration() {
    if (getString(DEFAULT_LIST_PREF, "")!! != DEFAULT_LIST) {
        edit()
            .remove("overrideBaseUrl")
            .putString(DEFAULT_LIST_PREF, DEFAULT_LIST)
            .setUrlList(DEFAULT_LIST, urlIndex)
            .apply()
    }
}

fun SharedPreferences.Editor.setUrlList(urlList: String, oldIndex: Int): SharedPreferences.Editor {
    putString(URL_LIST_PREF, urlList)
    val maxIndex = urlList.count { it == ',' }
    if (oldIndex in 0..maxIndex) return this
    val newIndex = Random.nextInt(0, maxIndex + 1)
    return putString(URL_INDEX_PREF, newIndex.toString())
}

class UpdateUrlInterceptor(private val preferences: SharedPreferences) : Interceptor {
    private val baseUrl = preferences.baseUrl
    var isUpdated = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.toString().startsWith(baseUrl)) return chain.proceed(request)

        val failedResponse = try {
            val response = chain.proceed(request)
            if (response.isSuccessful && response.header("Server") != "Parking/1.0") return response
            response.close()
            Result.success(response)
        } catch (e: Throwable) {
            if (chain.call().isCanceled()) throw e
            Result.failure(e)
        }

        if (isUpdated || updateUrl(chain)) {
            throw IOException("网址已自动更新，请重启应用")
        }
        return failedResponse.getOrThrow()
    }

    @Synchronized
    private fun updateUrl(chain: Interceptor.Chain): Boolean {
        if (isUpdated) return true
        val response = try {
            chain.proceed(GET("https://stevenyomi.github.io/source-domains/wnacg.txt"))
        } catch (_: Throwable) {
            return false
        }
        if (!response.isSuccessful) {
            response.close()
            return false
        }
        val newList = response.body.string()
        if (newList != preferences.getString(URL_LIST_PREF, "")!!) {
            preferences.edit()
                .setUrlList(newList, preferences.urlIndex)
                .apply()
        }
        isUpdated = true
        return true
    }
}

private const val DEFAULT_LIST_PREF = "defaultBaseUrl"
private const val URL_LIST_PREF = "baseUrlList"
private const val URL_INDEX_PREF = "baseUrlIndex"
private const val TITLE_BLACKLIST_PREF = "titleBlacklist"
private const val BLACKLIST_MAX_SCAN_PAGES_PREF = "blacklistMaxScanPages"
private const val FILTER_SEARCH_RESULTS_PREF = "filterSearchResults"
private const val DEFAULT_MAX_SCAN_PAGES = 5
private const val TITLE_BLACKLIST_SUMMARY = "使用英文逗号或换行分隔多个关键词；忽略大小写，始终作用于热门和最新列表。"
private const val BLACKLIST_MAX_SCAN_PAGES_SUMMARY =
    "每次加载最多检查多少个网站页面，包含当前页。找到未被屏蔽的内容后立即返回；只在启用黑名单过滤时生效。"
