package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.RELEASE_PAGE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("PropertyName")
@Serializable
@SerialName(RELEASE_PAGE)
class PageDataDto(
    val is_page_paid: Boolean,
    val desktop_image_url: String,
    val mobile_image_url: String,
) : EntityDto()
