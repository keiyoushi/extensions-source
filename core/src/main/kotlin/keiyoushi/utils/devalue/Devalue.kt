package keiyoushi.utils.devalue

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

object Devalue {
    fun decode(
        text: String,
        revivers: Map<String, DevalueReviver> = emptyMap(),
    ): JsonElement {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return JsonNull
        return DevalueParser(revivers).parse(trimmed)
    }
}

fun String.decodeDevalue(
    revivers: Map<String, DevalueReviver> = emptyMap(),
): JsonElement = Devalue.decode(this, revivers)
