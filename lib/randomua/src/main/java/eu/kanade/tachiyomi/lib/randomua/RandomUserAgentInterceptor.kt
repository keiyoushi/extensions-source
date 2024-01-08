package eu.kanade.tachiyomi.lib.randomua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

private class RandomUserAgentInterceptor(
    private val userAgentType: UserAgentType,
    private val customUA: String?,
    private val filterInclude: List<String>,
    private val filterExclude: List<String>,
) : Interceptor {

    private var userAgent: String? = null

    private val json: Json by injectLazy()

    private val network: NetworkHelper by injectLazy()

    private val client = network.client

    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            val originalRequest = chain.request()

            val newUserAgent = getUserAgent()
                ?: return chain.proceed(originalRequest)

            val originalHeaders = originalRequest.headers

            val modifiedHeaders = originalHeaders.newBuilder()
                .set("User-Agent", newUserAgent)
                .build()

            return chain.proceed(
                originalRequest.newBuilder()
                    .headers(modifiedHeaders)
                    .build()
            )
        } catch (e: Exception) {
            throw IOException(e.message)
        }
    }

    private fun getUserAgent(): String? {
        if (userAgentType == UserAgentType.OFF) {
            return customUA?.ifBlank { null }
        }

        if (!userAgent.isNullOrEmpty()) return userAgent

        val uaResponse = client.newCall(GET(UA_DB_URL)).execute()

        if (!uaResponse.isSuccessful) {
            uaResponse.close()
            return null
        }

        val userAgentList = uaResponse.use { json.decodeFromString<UserAgentList>(it.body.string()) }

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

    companion object {
        private const val UA_DB_URL = "https://tachiyomiorg.github.io/user-agents/user-agents.json"
    }
}

/**
 * Helper function to add a latest random user agent interceptor.
 * The interceptor will added at the first position in the chain,
 * so the CloudflareInterceptor in the app will be able to make usage of it.
 *
 * @param userAgentType User Agent type one of (DESKTOP, MOBILE, OFF)
 * @param customUA  Optional custom user agent used when userAgentType is OFF
 * @param filterInclude Filter to only include User Agents containing these strings
 * @param filterExclude Filter to exclude User Agents containing these strings
 */
fun OkHttpClient.Builder.setRandomUserAgent(
    userAgentType: UserAgentType,
    customUA: String? = null,
    filterInclude: List<String> = emptyList(),
    filterExclude: List<String> = emptyList(),
) = apply {
    interceptors().add(0, RandomUserAgentInterceptor(userAgentType, customUA, filterInclude, filterExclude))
}

enum class UserAgentType {
    MOBILE,
    DESKTOP,
    OFF
}

@Serializable
private data class UserAgentList(
    val desktop: List<String>,
    val mobile: List<String>
)
