package eu.kanade.tachiyomi.extension.en.mehgazone.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Calendar

@Serializable
class ChapterListDto(
    val id: Int,
    @Serializable(DateTimeSerializer::class)
    @SerialName("date_gmt")
    val date: Calendar,
    val title: RenderedDto,
    val excerpt: RenderedDto,
)

@Serializable
class PageListDto(
    @Serializable(DateTimeSerializer::class)
    val date: Calendar,
    val title: RenderedDto,
    val link: String,
    val content: RenderedDto,
    val excerpt: RenderedDto,
)

@Serializable
class RenderedDto(
    val rendered: String,
)
