package eu.kanade.tachiyomi.extension.zh.wnacg

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.random.Random

private const val DEFAULT_LIST = "https://www.wn01.uk,https://www.wn05.cc,https://www.wn04.cc,https://www.wn03.cc"

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
)

val SharedPreferences.baseUrl: String
    get() {
        val list = urlList
        return list.getOrNull(urlIndex) ?: list[0]
    }

val SharedPreferences.urlIndex get() = getString(URL_INDEX_PREF, "-1")!!.toInt()
val SharedPreferences.urlList get() = getString(URL_LIST_PREF, DEFAULT_LIST)!!.split(",")

fun getCiBaseUrl() = DEFAULT_LIST.replace(",", "#, ")

fun getSharedPreferences(id: Long): SharedPreferences {
    val preferences: SharedPreferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    if (preferences.getString(DEFAULT_LIST_PREF, "")!! == DEFAULT_LIST) return preferences
    preferences.edit()
        .remove("overrideBaseUrl")
        .putString(DEFAULT_LIST_PREF, DEFAULT_LIST)
        .setUrlList(DEFAULT_LIST, preferences.urlIndex)
        .apply()
    return preferences
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
