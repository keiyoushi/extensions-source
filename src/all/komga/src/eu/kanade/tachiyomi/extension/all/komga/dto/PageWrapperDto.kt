package eu.kanade.tachiyomi.extension.all.komga.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
class PageWrapperDto<T>(
    val content: List<T>,
    val empty: Boolean,
    val first: Boolean,
    val last: Boolean,
    val number: Long,
    val numberOfElements: Long,
    val size: Long,
    val totalElements: Long,
    val totalPages: Long,
)
