package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(MDConstants.author)
data class AuthorDto(override val attributes: AuthorArtistAttributesDto? = null) : EntityDto()

@Serializable
@SerialName(MDConstants.artist)
data class ArtistDto(override val attributes: AuthorArtistAttributesDto? = null) : EntityDto()

@Serializable
data class AuthorArtistAttributesDto(val name: String) : AttributesDto()
