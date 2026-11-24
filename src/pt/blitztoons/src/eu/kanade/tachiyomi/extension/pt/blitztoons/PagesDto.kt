package eu.kanade.tachiyomi.extension.pt.blitztoons

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class PagesDto(private val paginas: List<String>) {
    fun toPageList() = paginas.mapIndexed { index, url -> Page(index, imageUrl = url) }
}
