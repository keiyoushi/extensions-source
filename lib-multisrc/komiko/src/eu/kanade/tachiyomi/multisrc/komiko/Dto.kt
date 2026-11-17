package eu.kanade.tachiyomi.multisrc.komiko

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RELEASE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val ZONE = ZoneId.of("Europe/Paris")

// Genres of the catalogue end with their count: "Action (247)"
private val GENRE_COUNT_REGEX = Regex("""\s*\(\d+\)$""")

@Serializable
class CatalogueDto(
    val produits: List<CatalogueProductDto>,
    val nb: Int,
)

@Serializable
class CatalogueProductDto(
    private val ref: Int,
    private val seo: String,
    private val titre: String,
    private val images: ImagesDto,
) {
    fun toSManga() = SManga.create().apply {
        url = "$seo/$ref"
        title = titre
        thumbnail_url = images.thumbnail
    }
}

@Serializable
class FiltersDto(
    val genres: List<GenreDto>,
)

@Serializable
class GenreDto(
    @SerialName("id_genre") val id: Int,
    @SerialName("genre") private val label: String,
) {
    val name get() = label.replace(GENRE_COUNT_REGEX, "")
}

@Serializable
class ImagesDto(
    @SerialName("img_grande") private val large: List<String>,
    @SerialName("img_mobile_400") private val small: List<String>,
) {
    val cover get() = large.firstOrNull()
    val thumbnail get() = small.firstOrNull()
}

// Without "produit" for a volume that doesn't exist, with a message such as "Erreur : Ce manga n'existe pas."
@Serializable
class ProductDto(
    val produit: ProductDetailsDto? = null,
    val message: String? = null,
)

@Serializable
class ProductDetailsDto(
    private val ref: Int,
    private val seo: String,
    private val titre: String,
    @SerialName("titre_licence_vo") private val originalTitle: String? = null,
    @SerialName("type_licence") private val type: String? = null,
    private val age: String? = null,
    @SerialName("date_parution") private val releaseDate: String? = null,
    private val description: String? = null,
    private val manga: CreditsDto? = null,
    private val genres: List<GenreDto>,
    private val images: ImagesDto,
    private val extraits: List<ExtractDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = "$seo/$ref"
        title = titre
        author = manga?.authors
        artist = manga?.artist
        description = MarkdownDescription.Builder()
            .info(type, age)
            .description(this@ProductDetailsDto.description?.let { Jsoup.parseBodyFragment(it).body() })
            .altTitles(listOfNotNull(originalTitle?.takeIf(String::isNotBlank)))
            .build()
        genre = genres.joinToString { it.name }.ifEmpty { null }
        thumbnail_url = images.cover
        // A volume yet to be released gets its preview later
        update_strategy = if (extraits.isEmpty()) UpdateStrategy.ALWAYS_UPDATE else UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toChapters(): List<SChapter> {
        if (extraits.isEmpty()) return emptyList()
        return listOf(
            SChapter.create().apply {
                url = ref.toString()
                name = "Extrait"
                date_upload = RELEASE_DATE_FORMAT.tryParseDate(releaseDate, ZONE)
                memo = buildJsonObject { putJsonArray("pages") { extraits.forEach { add(it.full) } } }
            },
        )
    }
}

@Serializable
class CreditsDto(
    private val auteur: String? = null,
    private val scenariste: String? = null,
    private val illustrateur: String? = null,
) {
    val authors get() = listOfNotNull(auteur, scenariste).filter(String::isNotBlank).distinct().joinToString().ifEmpty { null }
    val artist get() = illustrateur?.takeIf(String::isNotBlank)
}

@Serializable
class ExtractDto(
    val full: String,
)
