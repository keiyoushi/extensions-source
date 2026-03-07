package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 域名管理器
 * 负责从域名服务器获取和更新最新的 API 域名列表
 */
class DomainManager(
    private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) {
    data class DomainOption(
        val domain: String,
        val label: String? = null,
    )

    /**
     * 从域名服务器获取最新域名列表
     *
     * @return 成功返回域名列表，失败返回 null
     */
    fun fetchLatestDomains(): List<String>? = fetchLatestDomainOptions()?.map { it.domain }

    /**
     * 从域名服务器获取最新域名列表（包含显示标签）
     *
     * @return 成功返回域名+标签列表，失败返回 null
     */
    fun fetchLatestDomainOptions(): List<DomainOption>? {
        for (serverUrl in JmConstants.API_DOMAIN_SERVER_LIST) {
            try {
                val request = Request.Builder()
                    .url(serverUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) continue

                    val encryptedData = response.body?.string()?.trim() ?: continue
                    if (encryptedData.isEmpty()) continue

                    // 解密域名列表
                    val domains = JmCryptoTool.decryptDomainOptions(encryptedData)
                    if (domains.isNotEmpty()) {
                        return domains.map { DomainOption(it.domain, it.label) }
                    }
                }
            } catch (e: Exception) {
                // 当前服务器失败，尝试下一个
                continue
            }
        }

        return null
    }

    /**
     * 更新域名列表到 SharedPreferences
     *
     * @param domains 新的域名列表
     */
    fun updateDomains(domains: List<String>) {
        updateDomainOptions(domains.map { DomainOption(domain = it) })
    }

    /**
     * 更新域名列表和显示标签到 SharedPreferences
     */
    fun updateDomainOptions(domains: List<DomainOption>) {
        val labels = domains.map { it.label?.trim().orEmpty() }
        val hasAnyLabel = labels.any { it.isNotEmpty() }
        val labelJson = if (hasAnyLabel) {
            org.json.JSONArray().apply {
                labels.forEach { put(it) }
            }.toString()
        } else {
            null
        }

        preferences.edit()
            .putString(JmConstants.PREF_API_DOMAIN_LIST, domains.joinToString(",") { it.domain })
            .apply {
                if (labelJson != null) {
                    putString(JmConstants.PREF_API_DOMAIN_LABEL_LIST, labelJson)
                } else {
                    remove(JmConstants.PREF_API_DOMAIN_LABEL_LIST)
                }
            }
            .apply()
    }

    /**
     * 尝试更新域名列表
     * 如果获取失败，保持使用现有域名
     */
    fun tryUpdateDomains() {
        try {
            val latestDomains = fetchLatestDomainOptions()
            if (latestDomains != null && latestDomains.isNotEmpty()) {
                updateDomainOptions(latestDomains)
            }
        } catch (e: Exception) {
            // 更新失败，继续使用现有域名
        }
    }

    /**
     * 获取当前使用的域名
     */
    fun getCurrentDomain(): String {
        val domainList = preferences.getString(
            JmConstants.PREF_API_DOMAIN_LIST,
            JmConstants.API_DOMAIN_LIST.joinToString(","),
        )?.split(",") ?: JmConstants.API_DOMAIN_LIST.toList()

        val index = preferences.getApiDomainIndex()
        return domainList.getOrNull(index) ?: domainList.first()
    }

    /**
     * 获取当前域名列表
     */
    fun getDomainList(): List<String> = preferences.getString(
        JmConstants.PREF_API_DOMAIN_LIST,
        JmConstants.API_DOMAIN_LIST.joinToString(","),
    )?.split(",") ?: JmConstants.API_DOMAIN_LIST.toList()
}
