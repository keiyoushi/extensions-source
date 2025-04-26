package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.GlobalComixConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("PropertyName")
@Serializable
@SerialName(GlobalComixConstants.releasePage)
class PageDataDto(
    val is_page_paid: Boolean,
    val desktop_image_url: String,
    val mobile_image_url: String,
) : EntityDto()
