package eu.kanade.tachiyomi.extension.en.templescan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the short field names the site's Next.js RSC payload uses.
 *
 * The site renames a fixed set of fields to keys it derives at runtime, so any key literal
 * committed here goes stale as soon as the site rotates the salt. The derivation lives in the
 * site's own client bundle (module `14834`), which is why it can be reproduced and re-read
 * instead of hardcoded.
 */
internal object RscKeys {

    /** Last salt seen in the bundle; used until the payload proves it stale. */
    const val DEFAULT_SALT = "d5c68d61d4c2"

    /**
     * The renamed fields, in the exact order the bundle hashes them. Order is part of the
     * derivation: a collision bumps the counter and every later key shifts with it.
     */
    val FIELDS = listOf(
        "series_slug",
        "Season",
        "Chapter",
        "price",
        "title",
        "chapter_name",
        "chapter_slug",
        "images",
    )

    /** The bundle's own literal for [FIELDS]; finding it in a chunk is how the salt is located. */
    private val FIELD_LIST_LITERAL = FIELDS.joinToString(",") { "\"$it\"" }

    /** The salt is a hex literal immediately before the `":"` the derivation concatenates onto it. */
    private val SALT_REGEX = Regex("""["']([0-9a-f]{8,32})["']\s*,\s*["']:["']""")

    private const val FNV_OFFSET_BASIS = -2128831035 // 2166136261 as signed 32-bit
    private const val FNV_PRIME = 16777619

    /**
     * Maps each logical field name to the short key the payload uses for it, for a given [salt].
     */
    fun derive(salt: String): Map<String, String> {
        val used = mutableSetOf<String>()
        return FIELDS.associateWith { field ->
            var collisionIndex = 0
            var key: String
            do {
                key = encode(fnv1a32("$salt:$field:$collisionIndex"))
                collisionIndex++
            } while (key in used)
            used += key
            key
        }
    }

    /**
     * Rewrites every short key in [element] to its logical name, so the payload can be decoded
     * into the DTOs without them knowing about the salt at all.
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
     * tree. Names not in [FIELDS] are not renamed by the site, so they are matched as-is.
     *
     * [fields] must include at least one renamed field: a stale salt is only detectable when the
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

    /** Extracts the salt from a client chunk, or returns `null` if this chunk is not the one. */
    fun findSalt(chunkSource: String): String? {
        val fieldsAt = chunkSource.indexOf(FIELD_LIST_LITERAL)
        if (fieldsAt == -1) return null

        return SALT_REGEX.find(chunkSource, fieldsAt)?.groupValues?.get(1)
    }

    private fun fnv1a32(value: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (char in value) {
            hash = hash xor char.code
            hash *= FNV_PRIME
        }
        return hash
    }

    /** `chr(97 + hash % 26) + (hash >>> 5).toString(36)`, over the hash's unsigned 32 bits. */
    private fun encode(hash: Int): String {
        val unsigned = hash.toLong() and 0xFFFFFFFFL
        return ('a' + (unsigned % 26).toInt()) + (unsigned shr 5).toString(36)
    }
}
