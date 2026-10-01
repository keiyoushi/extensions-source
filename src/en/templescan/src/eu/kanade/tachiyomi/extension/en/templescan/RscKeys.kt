package eu.kanade.tachiyomi.extension.en.templescan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the short field names the site's Next.js RSC payload uses.
 *
 * The site renames a fixed set of fields to keys it ships in its own client bundle (module
 * `14834`) as a positional pair of lists: the logical names, then a comma-separated string of
 * keys. Reading that table from the bundle means a rebuild that renames the keys does not need
 * an extension update, unlike the key literals it replaces.
 */
internal object RscKeys {

    /**
     * The bundle's table: an array literal of logical names, then the variable holding the
     * comma-separated keys. Names are paired with keys by position, exactly as the site does it.
     */
    private val TABLE_REGEX = Regex(
        """\[((?:"[A-Za-z0-9_]+",?)+)],\s*[^=;]{1,32}=\s*"([^"]*)"\.split\(","\)""",
    )

    private val NAME_REGEX = Regex("\"([A-Za-z0-9_]+)\"")

    /** Reads the rename table from a client chunk, or returns `null` if this chunk is not the one. */
    fun findTable(chunkSource: String): Map<String, String>? {
        val match = TABLE_REGEX.find(chunkSource) ?: return null

        val names = NAME_REGEX.findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
        val keys = match.groupValues[2].split(",")
        // The bundle itself throws when it cannot name every field; treat that as "not this chunk".
        if (keys.size < names.size) return null

        return names.zip(keys).toMap()
    }

    /** Flattens [keys] into the single preference value that caches the table. */
    fun encode(keys: Map<String, String>): String = keys.entries.joinToString(",") { "${it.key}=${it.value}" }

    /** Reads back what [encode] wrote. */
    fun decode(stored: String): Map<String, String> = stored.split(",").mapNotNull { entry ->
        val separator = entry.indexOf('=')
        if (separator <= 0) null else entry.take(separator) to entry.substring(separator + 1)
    }.toMap()

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
