package eu.kanade.tachiyomi.extension.en.mehgazone.dto

import eu.kanade.tachiyomi.extension.en.mehgazone.serialization.DateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class ChapterListDto(
    val id: Int,
    @Serializable(DateSerializer::class)
    @SerialName("date_gmt")
    val date: Date,
    val title: RenderedDto,
    val excerpt: RenderedDto,
)
