package eu.kanade.tachiyomi.extension.en.hentairead

import kotlinx.serialization.Serializable

@Serializable
class Results(
    val results: List<Result>,
)

@Serializable
class Result(
    val id: Int,
    val text: String,
)
