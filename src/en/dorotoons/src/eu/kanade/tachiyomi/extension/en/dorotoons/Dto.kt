package eu.kanade.tachiyomi.extension.en.dorotoons

import kotlinx.serialization.Serializable

@Serializable
class ReaderDto(
    val pages: List<ReaderPageDto>,
)

@Serializable
class ReaderPageDto(
    val full: String,
    val lite: String,
)
