package keiyoushi.lib.clipstudioreader

import kotlinx.serialization.Serializable

@Serializable
class ViewerConfig(
    val api: String,
)

@Serializable
class TokenResponse(
    val token: String,
)

@Serializable
class MetaResponse(
    val content: MetaContent,
)

@Serializable
class MetaContent(
    val baseUrl: String,
)

@Serializable
class PreprocessSettings(
    private val obfuscateImage: Boolean,
    private val obfuscateImageKey: Int?,
) {
    val imageKey get() = obfuscateImageKey.takeIf { obfuscateImage }
}
