package eu.kanade.tachiyomi.extension.ar.stellarsaber

import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val success: Boolean,
    val data: SearchData,
)

@Serializable
class SearchData(
    val results: List<MangaDto>,
)

@Serializable
class MangaDto(
    val title: String,
    val url: String,
    val cover: String,
    val type: String,
)

@Serializable
class CdnKeyResponse(
    val data: CdnKeyData,
)

@Serializable
class CdnKeyData(
    val key: String,
)
