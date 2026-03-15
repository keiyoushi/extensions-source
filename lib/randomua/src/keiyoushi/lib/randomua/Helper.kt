package keiyoushi.lib.randomua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private var userAgent: String? = null
private val client = Injekt.get<NetworkHelper>().client

internal fun getRandomUserAgent(
    userAgentType: UserAgentType,
    filterInclude: List<String>,
    filterExclude: List<String>,
): String? {
    if (!userAgent.isNullOrEmpty()) return userAgent

    val uaResponse = client.newCall(GET("https://keiyoushi.github.io/user-agents/user-agents.json")).execute()

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
