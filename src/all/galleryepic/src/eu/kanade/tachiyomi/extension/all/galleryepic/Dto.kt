package eu.kanade.tachiyomi.extension.all.galleryepic

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable

const val IMAGE_CDN = "https://static.galleryepic.xyz/image"

interface GalleryDto {
    val createdAt: String
    val cover: String
    val images: List<String>
    fun toSManga(manga: SManga, lang: String): SManga
}

@Serializable
class CosplayDto(
    override val createdAt: String,
    override val cover: String,
    override val images: List<String>,
    private val charactersAndCosers: List<CharactersAndCosersDto>,
) : GalleryDto {
    override fun toSManga(manga: SManga, lang: String): SManga = manga.apply {
        val link = charactersAndCosers.first()
        val coser = link.coser.coserName(lang)
        val character = link.character.characterName(lang)
        val parody = link.character.parody?.parodyName(lang)
        title = buildString {
            append(coser)
            append(" – ")
            append(character)
            append(" Cosplay")
            parody?.let { append(" ($it)") }
        }
        author = coser
        genre = listOfNotNull(parody, character).joinToString()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        thumbnail_url = "$IMAGE_CDN/$cover"
        description = MarkdownDescription.Builder()
            .field("Cosplayer", coser)
            .field("Character", character)
            .field("Published", createdAt.substringBefore('T'))
            .build()
    }
}

@Serializable
class CharactersAndCosersDto(
    val coser: CoserDto,
    val character: CharacterDto,
)

@Serializable
class CoserDto(
    private val name: String,
    private val nameEnglish: String,
) {
    fun coserName(lang: String) = if (lang == "zh") name else nameEnglish
}

@Serializable
class CharacterDto(
    private val character: String,
    private val characterEnglish: String,
    val parody: ParodyDto? = null,
) {
    fun characterName(lang: String) = if (lang == "zh") character else characterEnglish
}

@Serializable
class ParodyDto(
    private val parody: String,
    private val parodyEnglish: String,
) {
    fun parodyName(lang: String) = if (lang == "zh") parody else parodyEnglish
}

@Serializable
class ParodyListDto(
    val parodies: List<ParodyFilterDto>,
)

@Serializable
class ParodyFilterDto(
    val id: Int,
    val name: String,
)

@Serializable
class AlbumDto(
    override val createdAt: String,
    override val cover: String,
    override val images: List<String>,
    private val title: String,
    private val titleEnglish: String,
    private val serie: SerieDto? = null,
    private val models: List<AlbumModelDto>,
) : GalleryDto {
    override fun toSManga(manga: SManga, lang: String): SManga = manga.apply {
        val albumTitle = if (lang == "zh") title else titleEnglish
        val modelNames = models.map { it.model.modelName(lang) }.filterNot { it == "-" }
        val series = serie?.serieName(lang)?.takeIf { it != "-" }

        this.title = albumTitle
        author = modelNames.joinToString().takeIf { it.isNotEmpty() }
        genre = series
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        thumbnail_url = "$IMAGE_CDN/$cover"
        description = MarkdownDescription.Builder()
            .field("Model", modelNames)
            .field("Series", series)
            .field("Published", createdAt.substringBefore('T'))
            .build()
    }
}

@Serializable
class AlbumModelDto(
    val model: ModelDto,
)

@Serializable
class ModelDto(
    private val name: String,
    private val nameEnglish: String,
) {
    fun modelName(lang: String) = if (lang == "zh") name else nameEnglish
}

@Serializable
class SerieDto(
    private val serie: String,
    private val serieEnglish: String,
) {
    fun serieName(lang: String) = if (lang == "zh") serie else serieEnglish
}
