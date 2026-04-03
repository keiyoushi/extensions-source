package eu.kanade.tachiyomi.extension.ja.docomo

import kotlinx.serialization.Serializable

@Serializable
class ChaptersResponse(
    val html: String,
    val hasNext: Boolean,
)

@Serializable
class CPhpResponse(
    val url: String,
)
