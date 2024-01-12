package eu.kanade.tachiyomi.extension.en.anchira

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okio.ByteString.Companion.decodeBase64

object AnchiraHelper {
    const val KEY = "fuck niggers and faggots, and death to all jews"

    val json = Json { ignoreUnknownKeys = true }

    fun getPathFromUrl(url: String) = "${url.split("/").reversed()[1]}/${url.split("/").last()}"

    inline fun <reified T> decodeBytes(body: ResponseBody): T {
        val encryptedText = body.string().decodeBase64()!!
        return json.decodeFromString(
            XXTEA.decryptToString(
                encryptedText.toByteArray(),
                key = KEY,
            )!!,
        )
    }

    fun prepareTags(tags: List<Tag>) = tags.map {
        if (it.namespace == null) {
            it.namespace = 6
        }
        it
    }.sortedBy { it.namespace }.map {
        return@map it.name.lowercase()
    }.joinToString(", ") { it }
}
