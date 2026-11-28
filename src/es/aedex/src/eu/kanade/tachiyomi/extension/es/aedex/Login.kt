package eu.kanade.tachiyomi.extension.es.aedex

import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs

internal const val PREF_ACCESS_TOKEN = "pref_access_token"

private const val SESSION_PATH = "api/auth/session"

/**
 * The `access_token` the site's own client pulls from the NextAuth session and sends as
 * `Authorization: Bearer` on the backend and reader calls.
 */
internal fun Aedex.storedAccessToken(): String? = getPreferences().getString(PREF_ACCESS_TOKEN, null)

internal fun Aedex.storeAccessToken(token: String?) {
    getPreferences().edit().apply {
        if (token == null) remove(PREF_ACCESS_TOKEN) else putString(PREF_ACCESS_TOKEN, token)
    }.apply()
}

/**
 * Asks the site's session endpoint (authenticated by the session cookie) for a fresh access token.
 *
 * @return true if a new, different token was stored.
 */
internal suspend fun Aedex.refreshAccessToken(): Boolean {
    val token = runCatching {
        client.get("$baseUrl/$SESSION_PATH", headers, Fetch.XHR).parseAs<AedexSessionDto>().token()
    }.getOrNull()
    if (token == null || token == storedAccessToken()) return false
    storeAccessToken(token)
    return true
}
