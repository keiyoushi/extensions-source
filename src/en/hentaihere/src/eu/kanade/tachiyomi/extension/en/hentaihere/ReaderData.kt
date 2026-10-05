package eu.kanade.tachiyomi.extension.en.hentaihere

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class ReaderData(private val images: List<String>) {
    fun toPageList() = images.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
}
