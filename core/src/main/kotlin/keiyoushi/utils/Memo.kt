package keiyoushi.utils

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/**
 * Adds [block]'s keys to [SManga.memo], replacing keys of the same name and keeping the others (`memo["meta"]`
 * included).
 *
 * ```
 * putMemo {
 *     put("slug", slug)
 *     put("feedUrl", feedUrl.toJsonElement())
 * }
 * ```
 */
fun SManga.putMemo(block: JsonObjectBuilder.() -> Unit) = putMemo(buildJsonObject(block))

/** Adds the keys of [values] to [SManga.memo], replacing keys of the same name and keeping the others. */
fun SManga.putMemo(values: JsonObject) {
    memo = JsonObject(memo + values)
}

/** Adds [block]'s keys to [SChapter.memo], replacing keys of the same name and keeping the others. */
fun SChapter.putMemo(block: JsonObjectBuilder.() -> Unit) = putMemo(buildJsonObject(block))

/** Adds the keys of [values] to [SChapter.memo], replacing keys of the same name and keeping the others. */
fun SChapter.putMemo(values: JsonObject) {
    memo = JsonObject(memo + values)
}
