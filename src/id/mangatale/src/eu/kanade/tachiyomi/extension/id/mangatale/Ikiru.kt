package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class Ikiru :
    NatsuId(
        "Ikiru",
        "id",
        "https://05.ikiru.wtf",
    ) {
    // Formerly "MangaTale"
    override val id = 1532456597012176985

    override fun OkHttpClient.Builder.customizeClient() = rateLimit(12, 3.seconds).build().newBuilder()

    override fun transformJsonResponse(responseBody: String): String {
        val jsonStart = responseBody.indexOfFirst { it == '{' || it == '[' }
        return if (jsonStart >= 0) responseBody.substring(jsonStart) else responseBody
    }
}
