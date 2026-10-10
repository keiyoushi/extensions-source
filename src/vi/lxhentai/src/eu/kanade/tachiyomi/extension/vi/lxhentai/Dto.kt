package eu.kanade.tachiyomi.extension.vi.lxhentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TurnstilePayload(
    @SerialName("cf-turnstile-response")
    val turnstileResponse: String,
)

@Serializable
internal data class TokenResponse(
    val is_bot: Boolean = true,
    val action_token: String? = null,
)
