package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterDto(
    @SerialName("chapter_id") val id: String,
    @SerialName("chapter_title") val title: String,
    @SerialName("chapter_number") val number: Float,
    @SerialName("chapter_date_published") val datePublished: String,
    @SerialName("chapter_content") val content: String? = null,
)
