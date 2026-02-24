package keiyoushi.utils

import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.reflect.typeOf

private val NEXT_F_REGEX = Regex("""self\.__next_f\.push\(\s*(\[.*])\s*\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)

private fun <T> extractValueNextJS(
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
        val result = extractValueNextJS(child, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

private fun Document.extractAppRouterPayloads(): List<JsonElement> = select("script:not([src])")
    .map { it.data() }
    .filter { "self.__next_f.push" in it }
    .flatMap { script ->
        try {
            val raw = NEXT_F_REGEX.find(script)?.groupValues?.get(1) ?: return@flatMap emptyList()
            val arr = jsonInstance.parseToJsonElement(raw).jsonArray
            val content = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()

            extractRscPayloads(content)
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

private fun extractRscPayloads(body: String): List<JsonElement> {
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
            pos++
            val commaIdx = body.indexOf(',', pos)
            if (commaIdx == -1) break
            val byteLen = body.substring(pos, commaIdx).toIntOrNull(16) ?: break
            pos = commaIdx + 1
            // Advance exactly byteLen UTF-8 bytes
            var bytes = 0
            val start = pos
            while (pos < body.length && bytes < byteLen) {
                bytes += when {
                    body[pos].code < 0x80 -> 1
                    body[pos].code < 0x800 -> 2
                    else -> 3
                }
                pos++
            }
            try {
                results.add(jsonInstance.parseToJsonElement(body.substring(start, pos)))
            } catch (_: Exception) {}
        } else {
            // JSON chunk — parse by bracket depth
            val (element, end) = parseJsonAt(body, pos)
            if (element != null) results.add(element)
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
        // Primitives (no brackets): stop at next chunk boundary
        if (depth == 0 && (c.isWhitespace() || c == '\n')) {
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
 * Used internally by the no-predicate overloads of [Document.extractNextJs],
 * [String.extractNextJsRsc], and [Response.extractNextJs].
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

    return if (isList) {
        { element ->
            element is JsonArray &&
                requiredKeys.isNotEmpty() &&
                element.isNotEmpty() &&
                element.first() is JsonObject &&
                requiredKeys.all { it in element.first().jsonObject }
        }
    } else {
        { element ->
            element is JsonObject &&
                requiredKeys.isNotEmpty() &&
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
    val payloads = extractAppRouterPayloads().ifEmpty { extractPagesRouterPayloads() }
    for (payload in payloads) {
        val result = extractValueNextJS(payload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

/**
 * Reified overload of [Document.extractNextJs] that infers [deserializer] from [T].
 *
 * @param T Must be serializable via [kotlinx.serialization].
 * @see Document.extractNextJs
 */
inline fun <reified T> Document.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())

/**
 * Predicate-free overload of [Document.extractNextJs] that infers both the [deserializer]
 * and the matching predicate from [T]'s serial descriptor via [inferredNextJsPredicate].
 *
 * @param T Must be serializable via [kotlinx.serialization].
 * @see Document.extractNextJs
 * @see inferredNextJsPredicate
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
    for (payload in extractRscPayloads(this)) {
        val result = extractValueNextJS(payload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

/**
 * Reified overload of [String.extractNextJsRsc] that infers [deserializer] from [T].
 *
 * @param T Must be serializable via [kotlinx.serialization].
 * @see String.extractNextJsRsc
 */
inline fun <reified T> String.extractNextJsRsc(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJsRsc(predicate, serializer<T>())

/**
 * Predicate-free overload of [String.extractNextJsRsc] that infers both the [deserializer]
 * and the matching predicate from [T]'s serial descriptor via [inferredNextJsPredicate].
 *
 * @param T Must be serializable via [kotlinx.serialization].
 * @see String.extractNextJsRsc
 * @see inferredNextJsPredicate
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
 * Reified overload of [Response.extractNextJs] that infers [deserializer] from [T].
 *
 * @param T Must be serializable via [kotlinx.serialization].
 * @see Response.extractNextJs
 */
inline fun <reified T> Response.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())


/**
 * Predicate-free overload of [Response.extractNextJs] that infers both the [deserializer]
 * and the matching predicate from [T]'s serial descriptor via [inferredNextJsPredicate].
 *
 * @param T Must be serializable via [kotlinx.serialization].
 * @see Response.extractNextJs
 * @see inferredNextJsPredicate
 */
inline fun <reified T> Response.extractNextJs(): T? = extractNextJs(inferredNextJsPredicate<T>(), serializer<T>())
