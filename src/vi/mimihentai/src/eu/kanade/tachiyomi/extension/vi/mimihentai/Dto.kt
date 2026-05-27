package eu.kanade.tachiyomi.extension.vi.mimihentai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class LivewireMessageResponseDto(
    val serverMemo: JsonObject? = null,
)

@Serializable
class SyncInputRequestDto(
    val fingerprint: JsonObject,
    val serverMemo: JsonObject,
    val updates: List<SyncInputUpdateDto>,
)

@Serializable
class SyncInputUpdateDto(
    val type: String,
    val payload: SyncInputPayloadDto,
)

@Serializable
class SyncInputPayloadDto(
    val id: String,
    val name: String,
    val value: String,
)

@Serializable
class SubmitRequestDto(
    val fingerprint: JsonObject,
    val serverMemo: JsonObject,
    val updates: List<SubmitUpdateDto>,
)

@Serializable
class SubmitUpdateDto(
    val type: String,
    val payload: SubmitPayloadDto,
)

@Serializable
class SubmitPayloadDto(
    val id: String,
    val method: String,
    val params: List<String>,
)
