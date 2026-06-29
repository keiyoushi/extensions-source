package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.ARTIST
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("PropertyName")
@Serializable
@SerialName(ARTIST)
class ArtistDto(
    val name: String, // Slug
    val roman_name: String?,
) : EntityDto()
