package eu.kanade.tachiyomi.extension.en.hentaiforce

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ReaderDto(
    private val baseUriImg: String,
    private val pages: Map<Int, ReaderPageDto>,
) {
    fun imageUrls() = pages.toSortedMap().values.map { it.imageUrl(baseUriImg) }
}

@Serializable
class ReaderPageDto(
    @SerialName("l") private val dir: String,
    @SerialName("f") private val file: String,
) {
    fun imageUrl(baseUriImg: String) = baseUriImg.replace("%c", dir).replace("%s", file)
}
