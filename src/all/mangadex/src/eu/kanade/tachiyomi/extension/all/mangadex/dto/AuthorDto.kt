package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(Constants.AUTHOR)
data class AuthorDto(override val attributes: AuthorArtistAttributesDto? = null) : EntityDto()

@Serializable
@SerialName(Constants.ARTIST)
data class ArtistDto(override val attributes: AuthorArtistAttributesDto? = null) : EntityDto()

@Serializable
data class AuthorArtistAttributesDto(val name: String) : AttributesDto()
