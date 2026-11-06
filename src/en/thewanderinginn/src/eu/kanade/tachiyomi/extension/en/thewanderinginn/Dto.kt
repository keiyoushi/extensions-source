package eu.kanade.tachiyomi.extension.en.thewanderinginn

import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class TermDto(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
class PostDto(
    @SerialName("date_gmt") private val dateGmt: String,
    val title: RenderedDto,
    @SerialName("class_list") private val classList: List<String>,
) {
    val isLocked get() = "post-password-required" in classList

    val date get() = Instant.tryParse("${dateGmt}Z")
}

@Serializable
class PageDto(
    val content: RenderedDto,
)

@Serializable
class CategoriesDto(
    val categories: List<Int>,
)

@Serializable
class RenderedDto(
    val rendered: String,
)
