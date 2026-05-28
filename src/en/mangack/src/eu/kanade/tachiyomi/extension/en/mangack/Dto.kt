package eu.kanade.tachiyomi.extension.en.mangack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    private val link: String,
    private val title: RenderedDto,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
) {
    fun link(): String = link
    fun title(): String = decodeEntities(title.rendered)
    fun coverUrl(): String? = embedded?.featuredMedia?.firstOrNull()?.sourceUrl
}

@Serializable
class RenderedDto(val rendered: String = "")

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<FeaturedMediaDto>? = null,
)

@Serializable
class FeaturedMediaDto(
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
class TermPayloadDto(
    val id: Int,
    val name: String,
)

@Serializable
class ChapterContentDto(
    private val content: RenderedDto? = null,
) {
    fun contentHtml(): String = content?.rendered.orEmpty()
}

private val htmlEntityRegex = Regex("""&(#?[a-zA-Z0-9]+);""")

internal fun decodeEntities(s: String): String = htmlEntityRegex.replace(s) { m ->
    when (val e = m.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos", "#39" -> "'"
        "nbsp" -> " "
        "hellip" -> "…"
        "mdash" -> "—"
        "ndash" -> "–"
        "rsquo", "#8217" -> "'"
        "lsquo", "#8216" -> "'"
        "rdquo", "#8221" -> "\""
        "ldquo", "#8220" -> "\""
        else -> if (e.startsWith("#")) {
            e.removePrefix("#").toIntOrNull()?.toChar()?.toString() ?: m.value
        } else {
            m.value
        }
    }
}
