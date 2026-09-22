// [AI助手声明] 本文件由 AI 助手于 2026-09-22 修改（标记 [FIX-AI]）。
// 修改内容: 将旧版(第三方 zip)的"镜像网址列表 + stevenyomi.github.io 自动更新"机制移植进官方 1.6 代码，
// 与官方新增的手动自定义网址共存（手动优先、留空回退镜像，见 Jinmantiantang.kt）。
// 修改清单见 G:\MotrixDown\sourcefix\fix\README.md；原版备份在 G:\MotrixDown\sourcefix\backup-original-base\。
package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal fun getPreferenceList(
    context: Context,
    preferences: SharedPreferences,
    isUrlUpdated: Boolean,
) = arrayOf(
    // [FIX-AI] 原版(官方1.6): 无此设置项，网址只能在自定义输入框手动填写。
    // 修复后: 移植旧版"使用镜像网址"列表选择 + 自动更新逻辑，作为手动网址留空时的回退方案。
    ListPreference(context).apply {
        val urlList = preferences.urlList
        val fullList = SITE_ENTRIES_ARRAY + urlList
        val fullDesc = SITE_ENTRIES_ARRAY_DESCRIPTION + Array(urlList.size) { "内地线路${it + 1}" }
        val count = fullList.size

        key = USE_MIRROR_URL_PREF
        title = "使用镜像网址（未手动填写自定义网址时生效）"
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

// [FIX-AI] 官方1.6: 本文件仅有 blockList。以下整段镜像/登录代码移植自旧版(第三方 zip)与未合并的 PR#19104。

/** 旧版镜像机制选出的域名(不含 scheme)，仅在未手动填写自定义网址时使用 */
internal val SharedPreferences.mirrorBaseUrl: String
    get() {
        val list = SITE_ENTRIES_ARRAY
        val index = mirrorIndex
        if (index in list.indices) return list[index]
        return urlList.getOrNull(index - list.size) ?: list[0]
    }

internal const val DEFAULT_BASE_URL = "https://18comic.vip"

internal const val BLOCK_PREF = "BLOCK_GENRES_LIST"

// 登录用户名：沿用官方 1.6 已有的 "username" 键（收藏夹自动识别功能在用），保证旧设置值直接生效
internal const val USERNAME_PREF = "username"

// 登录密码：PR#19104 新增
internal const val PASSWORD_PREF = "password"

// 仅清除登录会话 cookie（改账号/密码后强制重新登录生效）；AVS 年龄验证 cookie 有意保留
private val SESSION_COOKIE_NAMES = arrayOf("PHPSESSID", "jmc_id", "jmc_password")

internal fun clearSessionCookies(baseUrl: String) {
    val manager = CookieManager.getInstance()
    for (name in SESSION_COOKIE_NAMES) {
        manager.setCookie(baseUrl, "$name=; Max-Age=-1; Path=/")
    }
    manager.flush()
}

internal val SharedPreferences.blockList: List<String>
    get() = getString(BLOCK_PREF, "")!!.substringBefore("//").trim().lowercase().split(' ')

private const val USE_MIRROR_URL_PREF = "useMirrorWebsitePreference"

private val SITE_ENTRIES_ARRAY_DESCRIPTION get() = arrayOf(
    "主站1",
    "主站2",
    "东南亚线路1",
    "东南亚线路2",
)

// Please also update AndroidManifest
private val SITE_ENTRIES_ARRAY get() = arrayOf(
    "18comic.vip",
    "18comic.ink",
    "jmcomic-zzz.one",
    "jmcomic-zzz.org",
)

private const val DEFAULT_LIST = "18comic-ive.club,18comic-aspa.org,18comic-wantgo.cc"
private const val DEFAULT_LIST_PREF = "defaultBaseUrlList"
private const val URL_LIST_PREF = "baseUrlList"

private val SharedPreferences.mirrorIndex get() = getString(USE_MIRROR_URL_PREF, "0")!!.toInt()
private val SharedPreferences.urlList get() = getString(URL_LIST_PREF, DEFAULT_LIST)!!.split(",")

// [FIX-AI] 原版(第三方旧版): 迁移时会 edit().remove("overrideBaseUrl")。
// 修复后: 删除该行——在合并版里 overrideBaseUrl 是"手动自定义网址"的存储键，不能被镜像迁移清空。
fun SharedPreferences.preferenceMigration() {
    if (getString(DEFAULT_LIST_PREF, "")!! != DEFAULT_LIST) {
        edit()
            .putString(DEFAULT_LIST_PREF, DEFAULT_LIST)
            .setUrlList(DEFAULT_LIST, mirrorIndex)
            .apply()
    }
}

private fun SharedPreferences.Editor.setUrlList(urlList: String, oldIndex: Int): SharedPreferences.Editor {
    putString(URL_LIST_PREF, urlList)
    val maxIndex = SITE_ENTRIES_ARRAY.size + urlList.count { it == ',' }
    if (oldIndex in 0..maxIndex) return this
    return putString(USE_MIRROR_URL_PREF, maxIndex.toString())
}

/**
 * [FIX-AI] 旧版"直接获取网址"机制：主站请求失败时从 stevenyomi.github.io 拉取最新镜像域名列表，
 * 提示用户重启并选择镜像。移植进 1.6 后增加 enabled 开关：手动网址生效期间完全放行（手动优先级高）。
 */
class UpdateUrlInterceptor(
    private val preferences: SharedPreferences,
    private val enabled: () -> Boolean,
) : Interceptor {
    @Volatile
    var isUpdated = false
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!enabled()) return chain.proceed(request)
        val baseUrl = "https://" + preferences.mirrorBaseUrl
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
