package eu.kanade.tachiyomi.extension.en.mangamo.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    val id: Int? = null,
    val chapterNumber: Float? = null,
    val createdAt: Long? = null,
    val enabled: Boolean? = null,
    val name: String? = null,
    val onlyTransactional: Boolean? = null,
    val seriesId: Int? = null,
)
