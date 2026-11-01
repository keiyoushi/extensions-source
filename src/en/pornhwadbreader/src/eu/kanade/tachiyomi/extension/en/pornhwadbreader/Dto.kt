package eu.kanade.tachiyomi.extension.en.pornhwadbreader

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class ReaderChapter(private val pages: List<ReaderPage>) {
    fun toPages() = pages.sortedBy { it.ordinal }.mapIndexed { index, page -> Page(index, imageUrl = page.url) }
}

@Serializable
class ReaderPage(val ordinal: Int, val url: String)

@Serializable
class FilterData(val genres: List<String>, val years: List<String>)
