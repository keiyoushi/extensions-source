package eu.kanade.tachiyomi.extension.pt.lermanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
data class LmMangaDto(
    val slug: String,
    val title: LmContentDto,
    val content: LmContentDto? = null,
    @SerialName("_embedded") val embedded: LmEmbedDto? = null,
) {

    fun toSManga(): SManga = SManga.create().apply {
        title = this@LmMangaDto.title.rendered
        thumbnail_url = "${LerManga.IMG_CDN_URL}/${slug.first().uppercase()}/$slug/capa.jpg"
        description = content?.rendered?.let { Jsoup.parseBodyFragment(it) }?.text()?.trim()
        genre = embedded?.wpTerm.orEmpty().flatten()
            .filter { it.taxonomy == "generomanga" }
            .joinToString { it.name }
        url = "/mangas/$slug"
    }
}

@Serializable
data class LmContentDto(val rendered: String)

@Serializable
data class LmEmbedDto(@SerialName("wp:term") val wpTerm: List<List<LmTaxonomyDto>>)

@Serializable
data class LmTaxonomyDto(
    val name: String,
    val taxonomy: String,
)
