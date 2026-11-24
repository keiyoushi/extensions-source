package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import android.webkit.WebResourceResponse
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.array
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.sha256
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.Buffer
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class LunarDecryptor(private val serenity: LunarSerenity) {

    private val seedMutex = Mutex()

    suspend fun extractSeeds(document: Document, client: OkHttpClient): LunarSeeds {
        val rows = document.flightRows()
        val pageUrl = document.location().toHttpUrl()
        val groups = linkedMapOf<SeedComponent, MutableList<JsonObject>>()

        rows.forEachIndexed { index, row ->
            if (row.value.startsWith("[") || row.value.startsWith("{")) {
                val element = try {
                    row.value.parseAs<JsonElement>()
                } catch (_: SerializationException) {
                    null
                }
                element?.forEachSeedElement { reference, props ->
                    rows.componentAt(reference, index, pageUrl)?.let { groups.getOrPut(it) { mutableListOf() } += props }
                }
            }
        }
        if (groups.isEmpty()) error("Failed to find payload seeds")

        val seedGroups = groups.entries
            .sortedByDescending { (_, candidates) -> candidates.sumOf { it.size } }
            .map { (component, candidates) -> LunarSeedGroup(component.moduleId, component.chunkUrls, candidates) }
        val script = "(${javaClass.getResource(RUNTIME_ASSET)!!.readText()})(${seedGroups.toJsonString()})"
        val chunkUrls = groups.keys.flatMap { it.chunkUrls }.toSet()

        return try {
            seedMutex.withLock {
                runWebView<LunarSeeds?>(timeout = 15.seconds) {
                    interceptRequest { request ->
                        when {
                            request.url.host == null -> null
                            request.url.toString() in chunkUrls -> proxyRequest(request, client)
                            else -> WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    onPageFinished {
                        evaluateJs(script) { result -> resolve(result.parseAs<LunarSeeds?>()) }
                    }
                    loadData(pageUrl.resolve("/")!!.toString(), "")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IOException("Failed to find payload seeds", e)
        } ?: error("Failed to find payload seeds")
    }

    private data class SeedComponent(val moduleId: JsonElement, val chunkUrls: List<String>)

    private fun JsonElement.forEachSeedElement(block: (reference: String, props: JsonObject) -> Unit) {
        when (this) {
            is JsonArray -> {
                seedProps()?.let { block(get(1).string.removePrefix("\$L"), it) }
                forEach { it.forEachSeedElement(block) }
            }
            is JsonObject -> values.forEach { it.forEachSeedElement(block) }
            else -> Unit
        }
    }

    private fun JsonArray.seedProps(): JsonObject? {
        if (size != 4 || (get(0) as? JsonPrimitive)?.stringOrNull != "\$") return null
        if ((get(1) as? JsonPrimitive)?.stringOrNull?.startsWith("\$L") != true) return null
        val props = get(3) as? JsonObject ?: return null
        return props.takeIf { it.isNotEmpty() && it.values.all { value -> value is JsonPrimitive && value.isString } }
    }

    private class FlightRow(val id: String, val value: String)

    /** The client reference row [id] in effect at row [index]; ids repeat across the page's flight streams. */
    private fun List<FlightRow>.componentAt(id: String, index: Int, pageUrl: HttpUrl): SeedComponent? {
        val (rowIndex, row) = rowBefore(id, index) ?: return null
        if (!row.startsWith("I[")) return null
        val reference = row.substring(1).parseAs<JsonArray>()
        val chunkUrls = reference[1].array.map { chunk ->
            val path = chunk.string.takeUnless { it.startsWith("\$") }
                ?: rowBefore(chunk.string.substring(1), rowIndex)?.second?.parseAs<String>()
                ?: return null
            pageUrl.resolve(path)!!.toString()
        }
        return SeedComponent(reference[0], chunkUrls)
    }

    private fun List<FlightRow>.rowBefore(id: String, index: Int): Pair<Int, String>? {
        for (i in index - 1 downTo 0) {
            if (this[i].id == id) return i to this[i].value
        }
        return null
    }

    private fun Document.flightRows(): List<FlightRow> {
        val buffer = Buffer()
        select("script:not([src])")
            .map { it.data().trim() }
            .filter { it.startsWith(FLIGHT_PUSH) }
            .map { it.removePrefix(FLIGHT_PUSH).removeSuffix(";").removeSuffix(")").parseAs<JsonArray>() }
            .forEach { push ->
                when (push.getOrNull(0)?.stringOrNull) {
                    "1" -> buffer.writeUtf8(push[1].string)
                    "3" -> buffer.write(Base64.decode(push[1].string, Base64.DEFAULT))
                }
            }

        val rows = mutableListOf<FlightRow>()
        while (!buffer.exhausted()) {
            if (buffer[0] == NEWLINE) {
                buffer.skip(1)
                continue
            }
            val colon = buffer.indexOf(COLON)
            if (colon < 0) break
            val id = buffer.readUtf8(colon)
            buffer.skip(1)
            val value = if (!buffer.exhausted() && buffer[0] == TEXT_ROW) {
                buffer.skip(1)
                val length = buffer.readUtf8(buffer.indexOf(COMMA)).toLong(16)
                buffer.skip(1)
                buffer.readUtf8(length)
            } else {
                val end = buffer.indexOf(NEWLINE)
                if (end < 0) buffer.readUtf8() else buffer.readUtf8(end)
            }
            rows += FlightRow(id, value)
        }
        return rows
    }

    fun newNonce(): String = randomString(NONCE_LENGTH)

    /**
     * `mint()` in the site's bundle: a timestamped payload naming the chapter, stream-encrypted
     * with a key blended from both seeds. [unpack] takes the same nonce.
     */
    fun mint(seeds: LunarSeeds, slug: String, chapter: String, nonce: String): String {
        val key = blend(seeds.axis, seeds.pitch)
        require(key.isNotEmpty()) { "Failed to derive chapter key" }

        val timestamp = Clock.System.now().epochSeconds.toString(16)
        val payload = "$timestamp|$nonce|$slug|$chapter|${randomString(6)}"

        val offset = Random.nextInt(256)
        val out = ArrayList<Int>(payload.length + 1)
        out.add(offset)
        payload.forEachIndexed { i, char ->
            out.add(char.code xor key[(i + offset) % key.size] xor (offset + seeds.names.mintStep * i and 0xFF) and 0xFF)
        }

        return out.toByteArray().base64Url()
    }

    private fun blend(axis: String, pitch: String): IntArray {
        val digest = (axis + SEPARATOR + pitch).sha256()
        val size = maxOf(axis.length, pitch.length)

        return IntArray(size) { i ->
            axis[i % axis.length].code xor
                pitch[i % pitch.length].code xor
                (digest[i % 32].toInt() and 0xFF) xor
                (83 * i + 29 and 0xFF) and 0xFF
        }
    }

    /**
     * The response body is AES-CBC over a key derived from the first seed, the nonce that was
     * minted into the token, and - when the server enforces it - the Serenity key thumbprint.
     */
    fun unpack(sessionData: String, seeds: LunarSeeds, nonce: String): JsonObject {
        val ciphertext = Base64.decode(
            sessionData.replace('-', '+').replace('_', '/'),
            Base64.DEFAULT,
        )
        val base = seeds.axis + SEPARATOR + nonce

        for (material in listOf(base + THUMBPRINT_SEPARATOR + serenity.thumbprint, base)) {
            val plaintext = try {
                ciphertext.aesDecrypt(aesCbc(material.sha256(), ByteArray(16))).decodeToString()
            } catch (_: GeneralSecurityException) {
                continue
            }

            if (plaintext.startsWith("{")) return plaintext.parseAs<JsonObject>().renamed(seeds.names.inner)
        }
        error("Failed to decrypt chapter session data")
    }

    private fun randomString(length: Int) = String(CharArray(length) { ALPHABET[Random.nextInt(ALPHABET.length)] })

    private fun List<Int>.toByteArray() = ByteArray(size) { this[it].toByte() }

    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val NONCE_LENGTH = 12
        private const val SEPARATOR = "\u0001"
        private const val THUMBPRINT_SEPARATOR = "\u0002"
        private const val RUNTIME_ASSET = "/assets/lunar-seed-runtime.js"
        private const val FLIGHT_PUSH = "self.__next_f.push("
        private const val NEWLINE = '\n'.code.toByte()
        private const val COLON = ':'.code.toByte()
        private const val COMMA = ','.code.toByte()
        private const val TEXT_ROW = 'T'.code.toByte()
    }
}

internal fun JsonObject.renamed(pairs: List<List<String>>): JsonObject {
    fun JsonObject.rename(): JsonObject {
        val map = toMutableMap()
        pairs.forEach { (from, to) -> map.remove(from)?.let { map[to] = it } }
        return JsonObject(map)
    }

    val outer = rename().toMutableMap()
    (outer["data"] as? JsonObject)?.let { outer["data"] = it.rename() }
    return JsonObject(outer)
}
