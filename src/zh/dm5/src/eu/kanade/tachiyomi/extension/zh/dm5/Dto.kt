package eu.kanade.tachiyomi.extension.zh.dm5

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    @SerialName("PostContent") private val postContent: String,
    @SerialName("Poster") private val poster: String,
) {
    override fun toString() = "$poster: $postContent"
}
