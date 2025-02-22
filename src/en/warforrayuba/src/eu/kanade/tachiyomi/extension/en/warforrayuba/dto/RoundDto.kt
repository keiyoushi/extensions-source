package eu.kanade.tachiyomi.extension.en.warforrayuba.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class RoundDto(
    val title: String,
    val description: String,
    val artist: String,
    val author: String,
    val cover: String,
    val chapters: Map<Int, ChapterDto>,
)
