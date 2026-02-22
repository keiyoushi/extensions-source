package keiyoushi.utils

import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
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
private val NEXT_F_REGEX = Regex("""self\.__next_f\.push\(\s*(\[.*])\s*\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)

private fun <T> extractValueNextJS(
    payload: JsonElement,
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    if (payload !is JsonObject && payload !is JsonArray) return null
    if (predicate(payload)) {
        return try {
            jsonInstance.decodeFromJsonElement(deserializer, payload)
        } catch (_: SerializationException) {
            null
        }
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

private fun extractRscPayloads(body: String): List<JsonElement> = body.lines()
    .filter { it.isNotBlank() }
    .mapNotNull { line ->
        val colonIndex = line.indexOf(':')
        if (colonIndex == -1) return@mapNotNull null
        try {
            jsonInstance.parseToJsonElement(line.substring(colonIndex + 1))
        } catch (_: Exception) {
            null
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
 * Extracts all Next.js hydrated flight data payloads from the inline `<script>` tags of this
 * [Document] and returns the first nested element that fulfills the given [predicate],
 * deserialized as [T], or `null` if none was found.
 *
 * Supports both the App Router (Next.js >= 13, RSC flight data via `self.__next_f.push`) and
 * the Pages Router (Next.js <= 12, JSON hydration via `<script id="__NEXT_DATA__">`).
 *
 * @param T The target type to deserialize the matched element into. Must be serializable via
 * [kotlinx.serialization].
 * @param predicate A function that receives each [JsonElement] candidate and returns `true`
 * for the element that should be extracted. Only [JsonObject] and [JsonArray] elements are
 * evaluated.
 * @return The first matching element deserialized as [T], or `null` if no match was found or
 * deserialization failed.
 */
inline fun <reified T> Document.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())

// ---- String (RSC) ----

/**
 * Parses this string as a raw RSC (React Server Components) flight response body and returns
 * the first nested element that fulfills the given [predicate], deserialized as [T] using the
 * provided [deserializer], or `null` if none was found.
 *
 * Use this when the RSC payload is fetched directly as a `text/x-component` response rather
 * than embedded in an HTML document, which occurs when Next.js performs client-side navigation
 * via fetch rather than a full page load.
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
 * Parses this string as a raw RSC (React Server Components) flight response body and returns
 * the first nested element that fulfills the given [predicate], deserialized as [T],
 * or `null` if none was found.
 *
 * Use this when the RSC payload is fetched directly as a `text/x-component` response rather
 * than embedded in an HTML document, which occurs when Next.js performs client-side navigation
 * via fetch rather than a full page load.
 *
 * @param T The target type to deserialize the matched element into. Must be serializable via
 * [kotlinx.serialization].
 * @param predicate A function that receives each [JsonElement] candidate and returns `true`
 * for the element that should be extracted. Only [JsonObject] and [JsonArray] elements are
 * evaluated.
 * @return The first matching element deserialized as [T], or `null` if no match was found or
 * deserialization failed.
 */
inline fun <reified T> String.extractNextJsRsc(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJsRsc(predicate, serializer<T>())

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
 * Consumes this [Response] body and extracts the first nested element that fulfills the given
 * [predicate], deserialized as [T], or `null` if none was found.
 *
 * Automatically dispatches to the appropriate extractor based on the `Content-Type` header:
 * - `text/x-component` — parsed as a raw RSC flight response via [String.extractNextJsRsc]
 * - `text/html` — parsed as an HTML document via [Document.extractNextJs]
 *
 * @param T The target type to deserialize the matched element into. Must be serializable via
 * [kotlinx.serialization].
 * @param predicate A function that receives each [JsonElement] candidate and returns `true`
 * for the element that should be extracted. Only [JsonObject] and [JsonArray] elements are
 * evaluated.
 * @return The first matching element deserialized as [T], or `null` if no match was found,
 * deserialization failed, or the response body was null.
 * @throws IllegalStateException if the `Content-Type` is neither `text/html` nor `text/x-component`.
 */
inline fun <reified T> Response.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())
