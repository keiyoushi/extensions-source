package eu.kanade.tachiyomi.extension.vi.vihentai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class LivewireInitialData(
    val fingerprint: JsonElement,
    val serverMemo: JsonElement,
)

@Serializable
class LivewireRequest(
    val fingerprint: JsonElement,
    val serverMemo: JsonElement,
    val updates: List<JsonElement>,
)

@Serializable
class LivewireUpdate<T>(
    val type: String,
    val payload: T,
)

@Serializable
class SyncInputPayload(
    val id: String,
    val name: String,
    val value: String,
)

@Serializable
class CallMethodPayload(
    val id: String,
    val method: String,
    val params: List<String>,
)
