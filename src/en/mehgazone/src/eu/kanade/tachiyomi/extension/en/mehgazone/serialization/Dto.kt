package eu.kanade.tachiyomi.extension.en.mehgazone.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val id: Int,
    @SerialName("date_gmt")
    val date: String,
    val title: RenderedDto,
    val excerpt: RenderedDto,
)

@Serializable
class PageListDto(
    val link: String,
    val content: RenderedDto,
    val excerpt: RenderedDto,
)

@Serializable
class RenderedDto(
    val rendered: String,
)
