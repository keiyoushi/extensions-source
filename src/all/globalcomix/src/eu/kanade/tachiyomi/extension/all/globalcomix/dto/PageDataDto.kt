package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.releasePage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("PropertyName")
@Serializable
@SerialName(releasePage)
class PageDataDto(
    val is_page_paid: Boolean,
    val desktop_image_url: String,
    val mobile_image_url: String,
) : EntityDto()
