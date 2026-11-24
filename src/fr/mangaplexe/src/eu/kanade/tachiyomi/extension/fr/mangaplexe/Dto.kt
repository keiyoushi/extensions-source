package eu.kanade.tachiyomi.extension.fr.mangaplexe

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class CategoryDto(
    val id: Int,
    private val count: Int,
    private val description: String,
    @SerialName("_embedded") private val embedded: EmbeddedDto,
) {
    fun toSManga(manga: SManga) = SManga.create().apply {
        url = manga.url
        title = manga.title
        thumbnail_url = manga.thumbnail_url
        // A heroine's category sits in her series' one, a theme's in "Porno Hentai"
        genre = embedded.up.first().name.removeSuffix(" Hentai")
        description = MarkdownDescription.Builder()
            .field("Images", count.toString())
            // The site's descriptions end with the shortcode of its ad block
            .description(Jsoup.parseBodyFragment(this@CategoryDto.description.replace(SHORTCODE, "")).body())
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    companion object {
        private val SHORTCODE = Regex("""\[\w+[^\]]*]""")
    }
}

@Serializable
class EmbeddedDto(val up: List<ParentDto>)

@Serializable
class ParentDto(val name: String)

@Serializable
class PostDto(
    @SerialName("date_gmt") val dateGmt: String,
    @SerialName("featured_media") val featuredMedia: Int,
)

@Serializable
class MediaDto(@SerialName("source_url") val sourceUrl: String)
