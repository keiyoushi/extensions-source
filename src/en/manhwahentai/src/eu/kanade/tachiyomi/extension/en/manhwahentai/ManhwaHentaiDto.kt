package eu.kanade.tachiyomi.extension.en.manhwahentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
class XhrResponseDto(
    val success: Boolean,
    val data: String,
)

@Serializable
class PageDto(
    val src: String,
)
