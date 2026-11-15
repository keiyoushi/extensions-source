package eu.kanade.tachiyomi.extension.en.yiffcomics

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The reader's data, whose page files are named after a prefix shared by the comic's pages
@Serializable
class ReaderDto(
    private val prefix: String,
    private val pages: List<ReaderPageDto>,
) {
    val pageCount get() = pages.size

    fun toPages() = pages.mapIndexed { index, page -> Page(index, imageUrl = page.url(prefix)) }
}

@Serializable
class ReaderPageDto(@SerialName("m") private val file: String) {
    fun url(prefix: String) = prefix + file
}

@Serializable
class TagListsDto(val tags: List<TagDto>, val characters: List<TagDto>)

@Serializable
class TagDto(val name: String, val slug: String)
