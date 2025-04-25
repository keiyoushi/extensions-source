package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.GlobalComixConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("PropertyName")
@Serializable
@SerialName(GlobalComixConstants.artist)
class ArtistDto(
    val name: String, // Slug
    val roman_name: String?,
) : EntityDto()
