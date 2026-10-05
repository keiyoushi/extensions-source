package eu.kanade.tachiyomi.extension.en.templescan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the short field names the site's Next.js RSC payload uses.
 *
 * The site renames a fixed set of fields to keys it derives in its own client bundle (module
 * `14834`): a positional list of the logical names, then a salt string that each name slices a
 * key out of. Reading that table from the bundle means a rebuild that rotates the salt does not
 * need an extension update, unlike the key literals it replaces.
 */
internal object RscKeys {

    /**
     * The bundle's table: an array literal of logical names, then the variable holding the salt
     * string, e.g. `["Chapter",...],a="o682...fk";`.
     */
    private val TABLE_REGEX = Regex(
        """\[((?:"[A-Za-z0-9_]+",?)+)],\s*([A-Za-z0-9_$]{1,40})\s*=\s*"([A-Za-z0-9]+)"""",
    )

    private val NAME_REGEX = Regex("\"([A-Za-z0-9_]+)\"")

    /** The rotation applied to every slot index, e.g. `l=1+a.charCodeAt(0)%15`. */
    private val OFFSET_REGEX = Regex(
        """([A-Za-z0-9_$]{1,40})\s*=\s*(\d+)\s*\+\s*([A-Za-z0-9_$]{1,40})\.charCodeAt\(0\)\s*%\s*(\d+)""",
    )

    /** The slot count and the per-slot slice length, e.g. `(t+l)%16*7`. */
    private val SLOT_REGEX = Regex(
        """\(\s*[A-Za-z0-9_$]{1,40}\s*\+\s*([A-Za-z0-9_$]{1,40})\s*\)\s*%\s*(\d+)\s*\*\s*(\d+)""",
    )

    /** How far past the names array the minified derivation may sit before it is not the one. */
    private const val DERIVATION_WINDOW = 600

    /** Reads the rename table from a client chunk, or returns `null` if this chunk is not the one. */
    fun findTable(chunkSource: String): Map<String, String>? {
        for (match in TABLE_REGEX.findAll(chunkSource)) {
            val names = NAME_REGEX.findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
            val saltVar = match.groupValues[2]
            val salt = match.groupValues[3]

            val derivation = chunkSource.substring(
                match.range.last + 1,
                minOf(chunkSource.length, match.range.last + 1 + DERIVATION_WINDOW),
            )
            val offset = OFFSET_REGEX.findAll(derivation).firstOrNull { it.groupValues[3] == saltVar } ?: continue
            val slot = SLOT_REGEX.findAll(derivation).firstOrNull { it.groupValues[1] == offset.groupValues[1] } ?: continue

            val slotCount = slot.groupValues[2].toInt()
            val keyLength = slot.groupValues[3].toInt()
            // The bundle's own guard: the salt must divide evenly into its slots and name every field.
            if (salt.length != slotCount * keyLength || names.size > slotCount) continue

            val start = offset.groupValues[2].toInt() + salt[0].code % offset.groupValues[4].toInt()
            return names.mapIndexed { index, name ->
                val position = (index + start) % slotCount * keyLength
                name to salt.substring(position, position + keyLength)
            }.toMap()
        }

        return null
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
