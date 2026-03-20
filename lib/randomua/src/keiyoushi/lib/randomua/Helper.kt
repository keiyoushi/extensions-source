package keiyoushi.lib.randomua

import android.os.Looper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.brotli.BrotliInterceptor
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private var userAgent: String? = null
private val client = Injekt.get<NetworkHelper>().client.newBuilder()
    .addNetworkInterceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .header("Cache-Control", "max-age=${24 * 60 * 60}")
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .build()
    }
    .apply {
        val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
        if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
    }
    .build()

internal fun getRandomUserAgent(
    userAgentType: UserAgentType,
    filterInclude: List<String>,
    filterExclude: List<String>,
): String? {
    if (!userAgent.isNullOrEmpty()) return userAgent

    // avoid network on main thread when webview screen accesses headers
    val uaRequest = if (Looper.myLooper() == Looper.getMainLooper()) {
        GET(UA_DB_URL, cache = CacheControl.FORCE_CACHE)
    } else {
        GET(UA_DB_URL)
    }

    val uaResponse = runBlocking(Dispatchers.IO) { client.newCall(uaRequest).await() }

    if (!uaResponse.isSuccessful) {
        uaResponse.close()
        return null
    }

    val userAgentList = uaResponse.parseAs<UserAgentList>()

    return when (userAgentType) {
        UserAgentType.DESKTOP -> userAgentList.desktop
        UserAgentType.MOBILE -> userAgentList.mobile
        else -> error("Expected UserAgentType.DESKTOP or UserAgentType.MOBILE but got UserAgentType.${userAgentType.name} instead")
    }
        .filter {
            filterInclude.isEmpty() || filterInclude.any { filter ->
                it.contains(filter, ignoreCase = true)
            }
        }
        .filterNot {
            filterExclude.any { filter ->
                it.contains(filter, ignoreCase = true)
            }
        }
        .randomOrNull()
        .also { userAgent = it }
}

private const val UA_DB_URL = "https://keiyoushi.github.io/user-agents/user-agents.json"

enum class UserAgentType {
    MOBILE,
    DESKTOP,
    OFF,
}

@Serializable
private class UserAgentList(
    val desktop: List<String>,
    val mobile: List<String>,
)
