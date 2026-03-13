package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference

/**
 * 禁漫天堂 API 版插件设置界面
 */
internal fun getPreferenceList(
    context: Context,
    preferences: SharedPreferences,
) = arrayOf(
    // API 域名选择
    ListPreference(context).apply {
        key = JmConstants.PREF_API_DOMAIN_INDEX
        title = "API 域名"

        val domainList = preferences.getString(
            JmConstants.PREF_API_DOMAIN_LIST,
            JmConstants.API_DOMAIN_LIST.joinToString(","),
        )?.split(",") ?: JmConstants.API_DOMAIN_LIST.toList()
        val domainLabels = parseDomainLabels(
            preferences.getString(JmConstants.PREF_API_DOMAIN_LABEL_LIST, null),
            domainList.size,
        )

        entries = Array(domainList.size) { index ->
            formatDomainEntry(domain = domainList[index], label = domainLabels.getOrNull(index).orEmpty())
        }
        entryValues = Array(domainList.size) { it.toString() }
        summary = "当前: %s\n线路会自动更新，切换后需要重启应用"
        setDefaultValue("0")
    },
)

private fun parseDomainLabels(labelJson: String?, size: Int): List<String> {
    if (labelJson.isNullOrBlank() || size <= 0) return emptyList()

    return runCatching {
        val array = org.json.JSONArray(labelJson)
        List(size) { index -> array.optString(index, "").trim() }
    }.getOrElse {
        emptyList()
    }
}

private fun formatDomainEntry(domain: String, label: String): String {
    val normalizedLabel = label.trim().removeSurrounding("\"")
    return when {
        normalizedLabel.isBlank() -> domain
        normalizedLabel == domain -> domain
        else -> "$normalizedLabel ($domain)"
    }
}
