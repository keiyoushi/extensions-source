package eu.kanade.tachiyomi.lib.clipstudioreader

import kotlinx.serialization.Serializable

// XML
class FaceData(
    val totalPages: Int,
    val scrambleWidth: Int,
    val scrambleHeight: Int,
)

class PagePart(
    val fileName: String,
    val type: Int,
    val isScrambled: Boolean,
)

// EPUB
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
    val obfuscateImageKey: Int,
)
