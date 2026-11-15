package eu.kanade.tachiyomi.extension.ja.hentaione

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class ViewerDto(private val slides: List<SlideDto>) {
    fun toPages() = slides.mapIndexed { index, slide -> slide.toPage(index) }
}

@Serializable
class SlideDto(private val src: String) {
    fun toPage(index: Int) = Page(index, imageUrl = src)
}
