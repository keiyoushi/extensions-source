package eu.kanade.tachiyomi.extension.vi.vlogtruyen
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDTO(
    val status: Boolean,
    val data: Data,
)

@Serializable
class Data(
    val chaptersHtml: String,
)
