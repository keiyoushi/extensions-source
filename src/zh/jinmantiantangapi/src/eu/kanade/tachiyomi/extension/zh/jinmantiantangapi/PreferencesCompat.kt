package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import android.content.SharedPreferences

internal fun SharedPreferences.getApiDomainIndex(): Int {
    val stringValue = runCatching {
        getString(JmConstants.PREF_API_DOMAIN_INDEX, null)
    }.getOrNull()
        ?.toIntOrNull()
    if (stringValue != null) return stringValue.coerceAtLeast(0)

    val intValue = runCatching {
        getInt(JmConstants.PREF_API_DOMAIN_INDEX, 0)
    }.getOrDefault(0)
    return intValue.coerceAtLeast(0)
}
