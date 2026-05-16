package keiyoushi.utils

import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.reflect.typeOf

private val NEXT_F_REGEX = Regex("""self\.__next_f\.push\(\s*(\[.*])\s*\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)

private fun <T> extractValueNextJs(
    payload: JsonElement,
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    if (payload !is JsonObject && payload !is JsonArray) return null
    if (predicate(payload)) {
        return jsonInstance.decodeFromJsonElement(deserializer, payload)
    }
    val children: Iterable<JsonElement> = when (payload) {
        is JsonObject -> payload.values
        is JsonArray -> payload
    }
    for (child in children) {
        val result = extractValueNextJs(child, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

/**
 * Recursively walks the JSON tree to resolve Next.js RSC references using the provided [chunkCache].
 * Mirrors the special value markers emitted by React Flight
 * ([ReactFlightServer.js](https://github.com/facebook/react/blob/main/packages/react-server/src/ReactFlightServer.js)):
 * - `$$` -> Escaped string, drops the first `$`.
 * - `$undefined` -> JS `undefined`, resolved to JSON null.
 * - `$D<iso>` -> Date, drops `$D` to expose the ISO-8601 string ([ReactFlightDate]).
 * - `$n<digits>` -> BigInt, drops `$n` to expose the digit string ([ReactFlightBigInt]).
 * - `$Infinity` / `$-Infinity` / `$NaN` / `$-0` -> drops `$`, exposes the token string
 *   ([ReactFlightNumber] parses it to the real `Double`; JSON itself has no Infinity/NaN).
 * - `$Q<id>` -> Map, the outlined model at `<id>` is an array of `[key, value]` entries.
 * - `$W<id>` -> Set, the outlined model at `<id>` is an array of values.
 * - `$<id>` -> Looks up the ID in our ByteText cache.
 *
 * [modelCache] holds outlined JSON model rows by hex id (for `$Q`/`$W` and lazy refs);
 * [chunkCache] holds binary ByteText chunks by hex id. [resolving] guards against
 * reference cycles between outlined models.
 */
private fun resolveNextJsRefs(
    element: JsonElement,
    chunkCache: Map<String, String>,
    modelCache: Map<String, JsonElement>,
    resolving: Set<String> = emptySet(),
): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { resolveNextJsRefs(it.value, chunkCache, modelCache, resolving) })
    is JsonArray -> JsonArray(element.map { resolveNextJsRefs(it, chunkCache, modelCache, resolving) })
    is JsonPrimitive -> {
        if (element.isString && element.content.startsWith("$") && element.content.length >= 2) {
            val str = element.content
            when {
                str == "\$undefined" -> JsonNull // JS undefined -> null
                // Non-finite / negative-zero -> strip '$', keep token as string for ReactFlightNumber.
                // JSON has no Infinity/NaN, so they can't live in the JsonElement tree as numbers.
                str == "\$Infinity" || str == "\$-Infinity" || str == "\$NaN" || str == "\$-0" ->
                    JsonPrimitive(str.substring(1))
                str[1] == '$' -> JsonPrimitive(str.substring(1)) // Escaped '$' -> keep one
                str[1] == 'D' -> JsonPrimitive(str.substring(2)) // Date -> strip '$D' for ReactFlightDate
                str[1] == 'n' -> JsonPrimitive(str.substring(2)) // BigInt -> strip '$n' for ReactFlightBigInt
                str[1] == 'Q' -> resolveMapRef(str.substring(2), chunkCache, modelCache, resolving) ?: element
                str[1] == 'W' -> resolveSetRef(str.substring(2), chunkCache, modelCache, resolving) ?: element
                // RSC chunk reference -> look up in cache and replace with content if found
                else -> chunkCache[str.substring(1)]?.let { JsonPrimitive(it) } ?: element
            }
        } else {
            element
        }
    }
}

/** Resolves the outlined model at [id] (a row of `[key, value]` pairs) into a [JsonObject]. */
private fun resolveMapRef(
    id: String,
    chunkCache: Map<String, String>,
    modelCache: Map<String, JsonElement>,
    resolving: Set<String>,
): JsonElement? {
    if (id in resolving) return null // cycle
    val entries = modelCache[id] as? JsonArray ?: return null
    val resolved = resolveNextJsRefs(entries, chunkCache, modelCache, resolving + id) as? JsonArray ?: return null
    val pairs = resolved.mapNotNull { (it as? JsonArray)?.takeIf { p -> p.size == 2 } }
    return JsonObject(
        pairs.associate { (k, v) ->
            val key = (k as? JsonPrimitive)?.content ?: k.toString()
            key to v
        },
    )
}

/** Resolves the outlined model at [id] (a row of values) into a [JsonArray]. */
private fun resolveSetRef(
    id: String,
    chunkCache: Map<String, String>,
    modelCache: Map<String, JsonElement>,
    resolving: Set<String>,
): JsonElement? {
    if (id in resolving) return null // cycle
    val values = modelCache[id] as? JsonArray ?: return null
    return resolveNextJsRefs(values, chunkCache, modelCache, resolving + id)
}

private fun Document.extractAppRouterPayloads(
    chunkCache: MutableMap<String, String>,
    modelCache: MutableMap<String, JsonElement>,
): List<JsonElement> = select("script:not([src])")
    .map { it.data() }
    .filter { "self.__next_f.push" in it }
    .flatMap { script ->
        try {
            val raw = NEXT_F_REGEX.find(script)?.groupValues?.get(1) ?: return@flatMap emptyList()
            val arr = jsonInstance.parseToJsonElement(raw).jsonArray
            val content = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()

            extractRscPayloads(content, chunkCache, modelCache)
        } catch (_: Exception) {
            emptyList()
        }
    }

private fun Document.extractPagesRouterPayloads(): List<JsonElement> {
    val data = selectFirst("script#__NEXT_DATA__")?.data() ?: return emptyList()
    return try {
        val root = jsonInstance.parseToJsonElement(data)
        val pageProps = root.jsonObject["props"]?.jsonObject?.get("pageProps")
        listOfNotNull(pageProps, root)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun extractRscPayloads(
    body: String,
    chunkCache: MutableMap<String, String>,
    modelCache: MutableMap<String, JsonElement>,
): List<JsonElement> {
    val results = mutableListOf<JsonElement>()
    var pos = 0

    while (pos < body.length) {
        // Find next `<hex>:` chunk header
        val colonIdx = body.indexOf(':', pos)
        if (colonIdx == -1) break

        // Validate everything before colon is hex digits
        val id = body.substring(pos, colonIdx)
        if (id.isEmpty() || !id.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            pos++
            continue
        }

        pos = colonIdx + 1
        if (pos >= body.length) break

        if (body[pos] == 'T') {
            // Binary chunk: T<hexLen>,<content>
            // byteLen is the UTF-8 byte length of the content, not the char count.
            pos++
            val commaIdx = body.indexOf(',', pos)
            if (commaIdx == -1) break
            val byteLen = body.substring(pos, commaIdx).toIntOrNull(16) ?: break
            pos = commaIdx + 1
            var bytes = 0
            val start = pos
            while (pos < body.length && bytes < byteLen) {
                // Count UTF-8 bytes per code point. Surrogate pairs (supplementary characters,
                // e.g. emoji) occupy 4 UTF-8 bytes; we consume both surrogate chars in one step.
                when {
                    body[pos].code < 0x80 -> bytes += 1
                    body[pos].code < 0x800 -> bytes += 2
                    Character.isHighSurrogate(body[pos]) -> {
                        bytes += 4
                        pos++ // consume the high surrogate; the loop increment handles the low
                    }

                    else -> bytes += 3
                }
                pos++
            }

            // Extract the content and cache it by its hex ID locally
            val chunkContent = body.substring(start, pos)
            chunkCache[id] = chunkContent

            try {
                results.add(jsonInstance.parseToJsonElement(chunkContent))
            } catch (_: Exception) {
            }
        } else {
            // JSON chunk — parse by bracket depth
            val (element, end) = parseJsonAt(body, pos)
            if (element != null) {
                results.add(element)
                modelCache[id] = element // outlined model row, referenced by $Q/$W
            }
            pos = end
        }
    }

    return results
}

/**
 * Attempts to parse a JSON value at [start] in [body], returning the parsed element
 * and the position immediately after the JSON value ends.
 */
private fun parseJsonAt(body: String, start: Int): Pair<JsonElement?, Int> {
    if (start >= body.length) return Pair(null, start)

    var depth = 0
    var inString = false
    var escape = false
    var i = start

    while (i < body.length) {
        val c = body[i++]
        if (escape) {
            escape = false
            continue
        }
        if (c == '\\' && inString) {
            escape = true
            continue
        }
        if (c == '"') {
            inString = !inString
            continue
        }
        if (inString) continue
        when (c) {
            '{', '[' -> depth++
            '}', ']' -> if (--depth == 0) {
                return try {
                    Pair(jsonInstance.parseToJsonElement(body.substring(start, i)), i)
                } catch (_: Exception) {
                    Pair(null, i)
                }
            }
        }
        if (depth == 0 && c.isWhitespace()) {
            return try {
                Pair(jsonInstance.parseToJsonElement(body.substring(start, i - 1)), i)
            } catch (_: Exception) {
                Pair(null, i)
            }
        }
    }
    return Pair(null, i)
}

/**
 * Builds a [JsonElement] predicate inferred from [T]'s serial descriptor, matching any
 * [JsonObject] that contains all non-optional, non-nullable fields of [T].
 *
 * Used internally by the predicate-free overloads of [Document.extractNextJs],
 * [String.extractNextJsRsc], and [Response.extractNextJs].
 *
 * @throws IllegalArgumentException if all fields of [T] are optional or nullable, as no
 * meaningful predicate can be inferred. Provide an explicit predicate in that case.
 */
@PublishedApi
internal inline fun <reified T> inferredNextJsPredicate(): (JsonElement) -> Boolean {
    val kType = typeOf<T>()
    val isList = kType.classifier == List::class

    val elementDescriptor = if (isList) {
        serializer<T>().descriptor.getElementDescriptor(0)
    } else {
        serializer<T>().descriptor
    }

    val requiredKeys = (0 until elementDescriptor.elementsCount)
        .filterNot { elementDescriptor.isElementOptional(it) || elementDescriptor.getElementDescriptor(it).isNullable }
        .map { elementDescriptor.getElementName(it) }
        .toSet()

    require(requiredKeys.isNotEmpty()) {
        "Cannot infer a predicate for ${elementDescriptor.serialName}: all fields are optional or nullable. Provide an explicit predicate instead."
    }

    return if (isList) {
        { element ->
            element is JsonArray &&
                element.isNotEmpty() &&
                element.first() is JsonObject &&
                requiredKeys.all { it in element.first().jsonObject }
        }
    } else {
        { element ->
            element is JsonObject &&
                requiredKeys.all { it in element }
        }
    }
}

// ---- Document ----

/**
 * Extracts all Next.js hydrated flight data payloads from the inline `<script>` tags of this
 * [Document] and returns the first nested element that fulfills the given [predicate],
 * deserialized as [T] using the provided [deserializer], or `null` if none was found.
 *
 * Supports both the App Router (Next.js >= 13, RSC flight data via `self.__next_f.push`) and
 * the Pages Router (Next.js <= 12, JSON hydration via `<script id="__NEXT_DATA__">`).
 *
 * @param T The target type to deserialize the matched element into.
 * @param predicate A function that receives each [JsonElement] candidate and returns `true`
 * for the element that should be extracted. Only [JsonObject] and [JsonArray] elements are
 * evaluated.
 * @param deserializer The [DeserializationStrategy] used to deserialize the matched element
 * into [T]. Prefer the [reified overload][extractNextJs] unless an explicit strategy is needed.
 * @return The first matching element deserialized as [T], or `null` if no match was found or
 * deserialization failed.
 */
fun <T> Document.extractNextJs(
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    val chunkCache = mutableMapOf<String, String>()
    val modelCache = mutableMapOf<String, JsonElement>()
    val payloads = extractAppRouterPayloads(chunkCache, modelCache).ifEmpty { extractPagesRouterPayloads() }

    for (payload in payloads) {
        val resolvedPayload = resolveNextJsRefs(payload, chunkCache, modelCache)
        val result = extractValueNextJs(resolvedPayload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

/**
 * Reified overload; infers [deserializer] from [T]. See the
 * [explicit-deserializer overload][Document.extractNextJs] for full documentation.
 *
 * @param T Must be serializable via [kotlinx.serialization].
 */
inline fun <reified T> Document.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())

/**
 * Predicate-free overload; infers both the deserializer and predicate from [T]'s serial
 * descriptor via [inferredNextJsPredicate]. See the
 * [explicit-deserializer overload][Document.extractNextJs] for full documentation.
 *
 * @param T Must be serializable via [kotlinx.serialization].
 */
inline fun <reified T> Document.extractNextJs(): T? = extractNextJs(inferredNextJsPredicate<T>(), serializer<T>())

// ---- String (RSC) ----

/**
 * Parses this string as a raw RSC (React Server Components) flight response body and returns
 * the first nested element that fulfills the given [predicate], deserialized as [T] using the
 * provided [deserializer], or `null` if none was found.
 *
 * Use this when the RSC payload is fetched directly as a `text/x-component` response rather
 * than embedded in an HTML document, which occurs when Next.js performs client-side navigation
 * via fetch rather than a full page load. For HTML documents, prefer [Document.extractNextJs].
 *
 * @param T The target type to deserialize the matched element into.
 * @param predicate A function that receives each [JsonElement] candidate and returns `true`
 * for the element that should be extracted. Only [JsonObject] and [JsonArray] elements are
 * evaluated.
 * @param deserializer The [DeserializationStrategy] used to deserialize the matched element
 * into [T]. Prefer the [reified overload][extractNextJsRsc] unless an explicit strategy is needed.
 * @return The first matching element deserialized as [T], or `null` if no match was found or
 * deserialization failed.
 */
fun <T> String.extractNextJsRsc(
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    val chunkCache = mutableMapOf<String, String>()
    val modelCache = mutableMapOf<String, JsonElement>()
    for (payload in extractRscPayloads(this, chunkCache, modelCache)) {
        val resolvedPayload = resolveNextJsRefs(payload, chunkCache, modelCache)
        val result = extractValueNextJs(resolvedPayload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

/**
 * Reified overload; infers [deserializer] from [T]. See the
 * [explicit-deserializer overload][String.extractNextJsRsc] for full documentation.
 *
 * @param T Must be serializable via [kotlinx.serialization].
 */
inline fun <reified T> String.extractNextJsRsc(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJsRsc(predicate, serializer<T>())

/**
 * Predicate-free overload; infers both the deserializer and predicate from [T]'s serial
 * descriptor via [inferredNextJsPredicate]. See the
 * [explicit-deserializer overload][String.extractNextJsRsc] for full documentation.
 *
 * @param T Must be serializable via [kotlinx.serialization].
 */
inline fun <reified T> String.extractNextJsRsc(): T? = extractNextJsRsc(inferredNextJsPredicate<T>(), serializer<T>())

// ---- OkHttp Response ----

/**
 * Consumes this [Response] body and extracts the first nested element that fulfills the given
 * [predicate], deserialized as [T] using the provided [deserializer], or `null` if none was found.
 *
 * Automatically dispatches to the appropriate extractor based on the `Content-Type` header:
 * - `text/x-component` — parsed as a raw RSC flight response via [String.extractNextJsRsc]
 * - `text/html` — parsed as an HTML document via [Document.extractNextJs]
 *
 * @param T The target type to deserialize the matched element into.
 * @param predicate A function that receives each [JsonElement] candidate and returns `true`
 * for the element that should be extracted. Only [JsonObject] and [JsonArray] elements are
 * evaluated.
 * @param deserializer The [DeserializationStrategy] used to deserialize the matched element
 * into [T]. Prefer the [reified overload][extractNextJs] unless an explicit strategy is needed.
 * @return The first matching element deserialized as [T], or `null` if no match was found,
 * deserialization failed, or the response body was null.
 * @throws IllegalStateException if the `Content-Type` is neither `text/html` nor `text/x-component`.
 */
fun <T> Response.extractNextJs(
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    val contentType = header("Content-Type") ?: ""
    return when {
        "text/x-component" in contentType -> body.string().extractNextJsRsc(predicate, deserializer)
        "text/html" in contentType -> asJsoup().extractNextJs(predicate, deserializer)
        else -> error("Unsupported Content-Type for Next.js extraction: $contentType")
    }
}

/**
 * Reified overload; infers [deserializer] from [T]. See the
 * [explicit-deserializer overload][Response.extractNextJs] for full documentation.
 *
 * @param T Must be serializable via [kotlinx.serialization].
 */
inline fun <reified T> Response.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())

/**
 * Predicate-free overload; infers both the deserializer and predicate from [T]'s serial
 * descriptor via [inferredNextJsPredicate]. See the
 * [explicit-deserializer overload][Response.extractNextJs] for full documentation.
 *
 * @param T Must be serializable via [kotlinx.serialization].
 */
inline fun <reified T> Response.extractNextJs(): T? = extractNextJs(inferredNextJsPredicate<T>(), serializer<T>())
