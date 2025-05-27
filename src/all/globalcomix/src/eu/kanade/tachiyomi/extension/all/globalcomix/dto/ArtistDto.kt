package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.artist
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("PropertyName")
@Serializable
@SerialName(artist)
class ArtistDto(
    val name: String, // Slug
    val roman_name: String?,
) : EntityDto()
