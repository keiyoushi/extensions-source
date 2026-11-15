package eu.kanade.tachiyomi.extension.pt.mangalivreblog

import kotlinx.serialization.Serializable

@Serializable
class ChaptersResponse(
    val data: ChaptersData,
)

@Serializable
class ChaptersData(
    val markup: String,
)
