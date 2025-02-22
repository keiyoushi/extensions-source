package eu.kanade.tachiyomi.extension.en.warforrayuba.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDto(
    val title: String,
    val volume: Int,
    val groups: ChapterGroupDto,
    val last_updated: Long,
)

@Serializable
data class ChapterGroupDto(
    val primary: String,
)
