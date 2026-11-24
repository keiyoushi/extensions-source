package eu.kanade.tachiyomi.extension.all.gocomics

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable

@Serializable
class CatalogueDto(val groupedFeatures: List<FeatureGroupDto>)

@Serializable
class FeatureGroupDto(val items: List<FeatureDto>)

@Serializable
class FeatureDto(
    private val name: String,
    val slug: String,
    val updatedToday: Boolean,
    private val creators: List<String>,
    private val badgeImage: ImageDto? = null,
    private val categories: List<CategoryDto>,
    val featureLanguage: String,
) {
    fun matches(query: String) = name.contains(query, ignoreCase = true) ||
        creators.any { it.contains(query, ignoreCase = true) } ||
        categories.any { it.name.contains(query, ignoreCase = true) }

    fun toSManga() = SManga.create().apply {
        url = "/$slug"
        title = name
        author = creators.joinToString().ifEmpty { null }
        genre = categories.joinToString { it.name }.ifEmpty { null }
        thumbnail_url = badgeImage?.url
    }
}

@Serializable
class ImageDto(val url: String)

@Serializable
class CategoryDto(val name: String)

@Serializable
class ComicSeriesDto(
    private val name: String,
    private val description: String? = null,
    private val author: List<PersonDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = name
        author = this@ComicSeriesDto.author.joinToString { it.name }.ifEmpty { null }
        description = MarkdownDescription.Builder().description(this@ComicSeriesDto.description).build()
    }
}

@Serializable
class PersonDto(val name: String)

@Serializable
class FeatureRunsDto(val dates: List<String>)
