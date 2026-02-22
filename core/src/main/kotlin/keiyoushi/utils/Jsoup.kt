package keiyoushi.utils

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
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

private fun Document.extractNextJSPayloads(): List<JsonElement> = select("script:not([src])")
    .map { it.data() }
    .filter { "self.__next_f.push" in it }
    .flatMap { script ->
        try {
            val raw = NEXT_F_REGEX.find(script)?.groupValues?.get(1) ?: return@flatMap emptyList()
            val arr = jsonInstance.parseToJsonElement(raw).jsonArray
            val content = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()

            content.lines()
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
        } catch (_: Exception) {
            emptyList()
        }
    }

/**
 * Extracts all Next.js hydrated flight data payloads from the inline `<script>` tags of this
 * [Document] and returns the first nested element that fulfills the given [predicate],
 * deserialized as [T] using the provided [deserializer], or `null` if none was found.
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
    for (payload in extractNextJSPayloads()) {
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
