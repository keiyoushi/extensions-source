package eu.kanade.tachiyomi.extension.en.templescan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the short field names the site's Next.js RSC payload uses.
 *
 * The site renames a fixed set of fields to keys it ships in its own client bundle (module
 * `14834`): an array of the logical names next to a string holding every key concatenated into
 * fixed-width slots, which the bundle rotates through by position. Reading that table from the
 * bundle means a rebuild that renames the keys does not need an extension update, unlike the key
 * literals it replaces.
 */
internal object RscKeys {

    /** Captures the logical names and the concatenated key blob of the bundle's rename table. */
    private val TABLE_REGEX = Regex(
        """\[((?:"[A-Za-z0-9_]+",?)+)],\s*[^=;]{1,32}=\s*"([A-Za-z0-9]+)"""",
    )

    private val NAME_REGEX = Regex("\"([A-Za-z0-9_]+)\"")

    /** The bundle lays the keys out in this many fixed-width slots and rotates through them. */
    private const val SLOT_COUNT = 16

    /** Reads the rename table from a client chunk, or returns `null` if this chunk is not the one. */
    fun findTable(chunkSource: String): Map<String, String>? {
        val match = TABLE_REGEX.find(chunkSource) ?: return null

        val names = NAME_REGEX.findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
        val slots = match.groupValues[2]
        // The bundle itself throws unless the keys fill the slots exactly and name at most one
        // field per slot; treat anything else as "not this chunk".
        if (slots.length % SLOT_COUNT != 0 || names.size > SLOT_COUNT) return null

        val keyLength = slots.length / SLOT_COUNT
        // The bundle seeds its rotation from the blob's first character, so the name at index i
        // lands on slot (i + rotation) % SLOT_COUNT rather than slot i.
        val rotation = 1 + slots[0].code % (SLOT_COUNT - 1)

        return names.mapIndexed { index, name ->
            val start = (index + rotation) % SLOT_COUNT * keyLength
            name to slots.substring(start, start + keyLength)
        }.toMap()
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
