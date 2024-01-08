package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

internal fun getPreferenceList(context: Context, preferences: SharedPreferences, isUrlUpdated: Boolean) = arrayOf(
    ListPreference(context).apply {
        key = MAINSITE_RATELIMIT_PREF
        title = "在限制时间内（下个设置项）允许的请求数量。"
        entries = Array(10) { "${it + 1}" }
        entryValues = Array(10) { "${it + 1}" }
        summary = "此值影响更新书架时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s"

        setDefaultValue(MAINSITE_RATELIMIT_PREF_DEFAULT)
    },

    ListPreference(context).apply {
        key = MAINSITE_RATELIMIT_PERIOD
        title = "限制持续时间。单位秒"
        entries = Array(60) { "${it + 1}" }
        entryValues = Array(60) { "${it + 1}" }
        summary = "此值影响更新书架时请求的间隔时间。调大此值可能减小IP被屏蔽的几率，但更新时间也会变慢。需要重启软件以生效。\n当前值：%s"

        setDefaultValue(MAINSITE_RATELIMIT_PERIOD_DEFAULT)
    },

    ListPreference(context).apply {
        val urlList = preferences.urlList
        val fullList = SITE_ENTRIES_ARRAY + urlList
        val fullDesc = SITE_ENTRIES_ARRAY_DESCRIPTION + Array(urlList.size) { "中国大陆线路${it + 1}" }
        val count = fullList.size

        key = USE_MIRROR_URL_PREF
        title = "使用镜像网址"
        entries = Array(count) { "${fullDesc[it]} (${fullList[it]})" }
        entryValues = Array(count) { "$it" }
        summary = if (isUrlUpdated) "%s\n镜像列表已自动更新，请选择合适的镜像并重启应用。" else "%s\n镜像列表会自动更新。重启后生效。"

        setDefaultValue("0")
    },

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

val SharedPreferences.baseUrl: String
    get() {
        val list = SITE_ENTRIES_ARRAY
        val index = mirrorIndex
        if (index in list.indices) return list[index]
        return urlList.getOrNull(index - list.size) ?: list[0]
    }

internal const val BLOCK_PREF = "BLOCK_GENRES_LIST"

internal const val MAINSITE_RATELIMIT_PREF = "mainSiteRateLimitPreference"
internal const val MAINSITE_RATELIMIT_PREF_DEFAULT = 1.toString()

internal const val MAINSITE_RATELIMIT_PERIOD = "mainSiteRateLimitPeriodPreference"
internal const val MAINSITE_RATELIMIT_PERIOD_DEFAULT = 3.toString()

private const val USE_MIRROR_URL_PREF = "useMirrorWebsitePreference"

private val SITE_ENTRIES_ARRAY_DESCRIPTION get() = arrayOf(
    "主站",
    "海外分流",
    "东南亚线路1",
    "东南亚线路2",
)

// List is based on https://jmcomic1.bet/
// Please also update AndroidManifest
private val SITE_ENTRIES_ARRAY get() = arrayOf(
    "18comic.vip",
    "18comic.org",
    "jmcomic.me",
    "jmcomic1.me",
)

private const val DEFAULT_LIST = "jm-comic3.art,jm-comic1.art,jm-comic2.ark"
private const val DEFAULT_LIST_PREF = "defaultBaseUrlList"
private const val URL_LIST_PREF = "baseUrlList"

private val SharedPreferences.mirrorIndex get() = getString(USE_MIRROR_URL_PREF, "0")!!.toInt()
private val SharedPreferences.urlList get() = getString(URL_LIST_PREF, DEFAULT_LIST)!!.split(",")

fun getSharedPreferences(id: Long): SharedPreferences {
    val preferences: SharedPreferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    if (preferences.getString(DEFAULT_LIST_PREF, "")!! == DEFAULT_LIST) return preferences
    preferences.edit()
        .remove("overrideBaseUrl")
        .putString(DEFAULT_LIST_PREF, DEFAULT_LIST)
        .setUrlList(DEFAULT_LIST, preferences.mirrorIndex)
        .apply()
    return preferences
}

fun SharedPreferences.Editor.setUrlList(urlList: String, oldIndex: Int): SharedPreferences.Editor {
    putString(URL_LIST_PREF, urlList)
    val maxIndex = SITE_ENTRIES_ARRAY.size + urlList.count { it == ',' }
    if (oldIndex in 0..maxIndex) return this
    return putString(USE_MIRROR_URL_PREF, maxIndex.toString())
}

class UpdateUrlInterceptor(private val preferences: SharedPreferences) : Interceptor {
    private val baseUrl = "https://" + preferences.baseUrl
    var isUpdated = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.toString().startsWith(baseUrl)) return chain.proceed(request)

        val failedResponse = try {
            val response = chain.proceed(request)
            if (response.isSuccessful) return response
            response.close()
            Result.success(response)
        } catch (e: Throwable) {
            if (chain.call().isCanceled() || e.message?.contains("Cloudflare") == true) throw e
            Result.failure(e)
        }

        if (isUpdated || updateUrl(chain)) {
            throw IOException("镜像网址已自动更新，请在插件设置中选择合适的镜像网址并重启应用（如果反复提示，可能是服务器故障）")
        }
        return failedResponse.getOrThrow()
    }

    @Synchronized
    private fun updateUrl(chain: Interceptor.Chain): Boolean {
        if (isUpdated) return true
        val response = try {
            chain.proceed(GET("https://stevenyomi.github.io/source-domains/jmcomic.txt"))
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
                .setUrlList(newList, preferences.mirrorIndex)
                .apply()
        }
        isUpdated = true
        return true
    }
}
