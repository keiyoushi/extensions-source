package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Ikiru : NatsuId(
    "Ikiru",
    "id",
    "https://02.ikiru.wtf",
) {
    // Formerly "MangaTale"
    override val id = 1532456597012176985

    override fun OkHttpClient.Builder.customizeClient() = rateLimit(12, 3)

    override fun transformJsonResponse(responseBody: String): String {
        val jsonStart = responseBody.indexOfFirst { it == '{' || it == '[' }
        return if (jsonStart >= 0) responseBody.substring(jsonStart) else responseBody
    }
}
