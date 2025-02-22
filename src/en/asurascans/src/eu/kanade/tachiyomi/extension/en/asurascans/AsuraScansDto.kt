package eu.kanade.tachiyomi.extension.en.asurascans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
class FiltersDto(
    val genres: List<FilterItemDto>,
    val statuses: List<FilterItemDto>,
    val types: List<FilterItemDto>,
)

@Serializable
class FilterItemDto(
    val id: Int,
    val name: String,
)

@Serializable
class PageDto(
    val order: Int,
    val url: String,
)
