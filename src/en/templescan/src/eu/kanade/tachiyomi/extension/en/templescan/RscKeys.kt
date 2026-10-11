package eu.kanade.tachiyomi.extension.en.templescan

import android.webkit.WebResourceResponse
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream

/**
 * Resolves the short field names the site's Next.js RSC payload uses.
 *
 * The site renames a fixed set of fields to short keys and renames them back with a function in
 * its client bundle. That function is found by what it does (see `assets/decode_rsc_keys.js`), so
 * the table survives changes to how the keys are derived and to where the decoder lives.
 */
internal object RscKeys {

    private val decodeScript by lazy { javaClass.getResource("/assets/decode_rsc_keys.js")!!.readText() }

    /**
     * Decodes [payloadKeys] with the decoder from [chunks] in a WebView confined to [baseUrl]'s
     * host, returning logical name to short key, or `null` if no module renames any of [fields].
     */
    suspend fun decodeTable(
        baseUrl: String,
        chunks: List<String>,
        payloadKeys: Collection<String>,
        fields: Collection<String>,
    ): Map<String, String>? {
        val host = baseUrl.toHttpUrl().host
        val args = listOf(chunks, payloadKeys.toList(), fields.toList()).joinToString { it.toJsonString() }
        val script = "($decodeScript)($args)" +
            ".then((table) => rscKeys.post(JSON.stringify(table)), () => rscKeys.post('null'))"

        return runWebView<Map<String, String>?> {
            blockImages = true
            interceptRequest { request ->
                val requestHost = request.url.host
                if (requestHost == null || requestHost == host) null else WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
            }
            jsBridge("rscKeys") { resolve(it.parseAs<Map<String, String>?>()) }
            onPageFinished { evaluateJs(script) }
            loadData(baseUrl, "")
        }?.filterValues { it in payloadKeys }
    }

    /**
     * Rewrites every short key in [element] to its logical name, so the payload can be decoded
     * into the DTOs without them knowing about the rename at all.
     */
    fun remap(element: JsonElement, keys: Map<String, String>): JsonElement {
        val byShortKey = keys.entries.associate { (field, key) -> key to field }

        fun rewrite(node: JsonElement): JsonElement = when (node) {
            is JsonObject -> JsonObject(node.entries.associate { (key, value) -> (byShortKey[key] ?: key) to rewrite(value) })
            is JsonArray -> JsonArray(node.map(::rewrite))
            else -> node
        }

        return rewrite(element)
    }

    /**
     * Builds the predicate that picks the payload node holding [fields] out of the resolved RSC
     * tree. Names the site does not rename are absent from [keys], so they are matched as-is.
     *
     * [fields] must include at least one renamed field: a stale table is only detectable when the
     * predicate stops matching, and a node identified purely by unrenamed keys (e.g. the
     * `seriesData` wrapper) matches either way and would silently decode to empty instead.
     */
    fun payloadPredicate(
        fields: List<String>,
        keys: Map<String, String>,
        isList: Boolean,
    ): (JsonElement) -> Boolean {
        val required = fields.map { keys[it] ?: it }.toSet()
        return if (isList) {
            { element ->
                element is JsonArray &&
                    element.isNotEmpty() &&
                    (element.first() as? JsonObject)?.keys?.containsAll(required) == true
            }
        } else {
            { element -> element is JsonObject && element.keys.containsAll(required) }
        }
    }
}
