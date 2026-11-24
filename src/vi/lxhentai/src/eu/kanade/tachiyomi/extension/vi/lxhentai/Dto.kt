package eu.kanade.tachiyomi.extension.vi.lxhentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal class TurnstilePayload(
    @SerialName("cf-turnstile-response")
    val turnstileResponse: String,
)

@Serializable
internal class TokenResponse(
    val is_bot: Boolean = true,
    val action_token: String? = null,
    val e: String? = null,
)

@Serializable
internal class EncryptedToken(
    val at: String? = null,
)

@Serializable
internal class LivewireInitialData(
    val fingerprint: JsonObject,
    val serverMemo: JsonObject,
)

@Serializable
internal class LivewireMessage(
    val fingerprint: JsonObject,
    val serverMemo: JsonObject,
    val updates: List<LivewireUpdate>,
)

@Serializable
internal class LivewireUpdate(
    val type: String,
    val payload: LivewireUpdatePayload,
)

@Serializable
internal class LivewireUpdatePayload(
    val id: String,
    val event: String,
    val params: List<String>,
)

@Serializable
internal class LivewireResponse(
    val effects: LivewireEffects? = null,
)

@Serializable
internal class LivewireEffects(
    val dispatches: List<LivewireDispatch> = emptyList(),
)

@Serializable
internal class LivewireDispatch(
    val event: String? = null,
    val data: LivewireDispatchData? = null,
)

@Serializable
internal class LivewireDispatchData(
    val data: String? = null,
)

@Serializable
internal class ImageManifest(
    val u: List<String> = emptyList(),
    val f: List<Int> = emptyList(),
)
