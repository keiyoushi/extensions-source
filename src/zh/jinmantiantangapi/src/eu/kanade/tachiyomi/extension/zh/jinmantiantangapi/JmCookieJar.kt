package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 禁漫天堂 Cookie 管理器
 *
 * 负责持久化存储和管理 API 请求所需的 Cookie
 * 特别处理登录后的 AVS cookie
 */
class JmCookieJar(private val preferences: SharedPreferences) : CookieJar {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        // 从 SharedPreferences 加载已保存的 Cookie
        loadCookies()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host

        // 保存到内存
        val hostCookies = cookieStore.getOrPut(host) { mutableListOf() }

        // 更新或添加 Cookie
        cookies.forEach { newCookie ->
            // 移除同名的旧 Cookie
            hostCookies.removeAll { it.name == newCookie.name }
            // 添加新 Cookie
            if (!newCookie.expiresAt.isExpired()) {
                hostCookies.add(newCookie)
            }
        }

        // 持久化到 SharedPreferences
        saveCookies()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()

        // 获取该域名的 Cookie
        val hostCookies = cookieStore[host] ?: return emptyList()

        // 过滤掉已过期的 Cookie
        val validCookies = hostCookies.filter { !it.expiresAt.isExpired() }

        // 如果有 Cookie 过期，更新存储
        if (validCookies.size != hostCookies.size) {
            hostCookies.clear()
            hostCookies.addAll(validCookies)
            saveCookies()
        }

        return validCookies
    }

    /**
     * 手动设置 Cookie（用于登录后设置 AVS 等特殊 Cookie）
     *
     * @param host 域名
     * @param cookieString Cookie 字符串，格式："name1=value1; name2=value2"
     */
    fun setCookies(host: String, cookieString: String) {
        val cookies = parseCookieString(host, cookieString)
        val hostCookies = cookieStore.getOrPut(host) { mutableListOf() }

        cookies.forEach { newCookie ->
            hostCookies.removeAll { it.name == newCookie.name }
            hostCookies.add(newCookie)
        }

        saveCookies()
    }

    /**
     * 获取指定域名的所有 Cookie
     */
    fun getCookies(host: String): List<Cookie> = cookieStore[host]?.filter { !it.expiresAt.isExpired() } ?: emptyList()

    /**
     * 获取指定域名的 Cookie 字符串
     * 格式："name1=value1; name2=value2"
     */
    fun getCookieString(host: String): String = getCookies(host).joinToString("; ") { "${it.name}=${it.value}" }

    /**
     * 清除所有 Cookie
     */
    fun clearAll() {
        cookieStore.clear()
        preferences.edit()
            .remove(JmConstants.PREF_LOGIN_COOKIE)
            .apply()
    }

    /**
     * 清除指定域名的 Cookie
     */
    fun clear(host: String) {
        cookieStore.remove(host)
        saveCookies()
    }

    /**
     * 从 SharedPreferences 加载 Cookie
     */
    private fun loadCookies() {
        val cookieString = preferences.getString(JmConstants.PREF_LOGIN_COOKIE, "") ?: ""
        if (cookieString.isEmpty()) return

        try {
            // Cookie 存储格式：host1|cookie1=value1; cookie2=value2||host2|cookie3=value3
            val hostCookiePairs = cookieString.split("||")

            hostCookiePairs.forEach { pair ->
                val parts = pair.split("|", limit = 2)
                if (parts.size == 2) {
                    val host = parts[0]
                    val cookies = parseCookieString(host, parts[1])
                    cookieStore[host] = cookies.toMutableList()
                }
            }
        } catch (e: Exception) {
            // 解析失败，清空
            cookieStore.clear()
        }
    }

    /**
     * 保存 Cookie 到 SharedPreferences
     */
    private fun saveCookies() {
        // 格式：host1|cookie1=value1; cookie2=value2||host2|cookie3=value3
        val cookieString = cookieStore.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("||") { (host, cookies) ->
                val cookieStr = cookies
                    .filter { !it.expiresAt.isExpired() }
                    .joinToString("; ") { "${it.name}=${it.value}" }
                "$host|$cookieStr"
            }

        preferences.edit()
            .putString(JmConstants.PREF_LOGIN_COOKIE, cookieString)
            .apply()
    }

    /**
     * 解析 Cookie 字符串为 Cookie 对象列表
     *
     * @param host 域名
     * @param cookieString Cookie 字符串，格式："name1=value1; name2=value2"
     */
    private fun parseCookieString(host: String, cookieString: String): List<Cookie> = cookieString.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.contains("=") }
        .mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                Cookie.Builder()
                    .name(parts[0].trim())
                    .value(parts[1].trim())
                    .domain(host)
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) // 30天
                    .build()
            } else {
                null
            }
        }

    /**
     * 检查 Cookie 是否过期
     */
    private fun Long.isExpired(): Boolean = this < System.currentTimeMillis()
}
