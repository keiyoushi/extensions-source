package eu.kanade.tachiyomi.extension.all.beauty3600000

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class PostDto(
    val id: Int,
    val link: String,
    val title: RenderedDto,
    val content: RenderedDto,
    val date: String,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@PostDto.title.rendered.takeIf { it.isNotBlank() } ?: throw Exception("Title is mandatory")
        thumbnail_url = Jsoup.parseBodyFragment(content.rendered).selectFirst("img")?.attr("src")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = "Gallery"
    }
}

@Serializable
class RenderedDto(
    val rendered: String,
)

@Serializable
class TermDto(
    val id: Int,
    val name: String,
    val slug: String,
)
