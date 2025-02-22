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
data class TagDto(
    @SerialName("tag_name") val name: String,
    @SerialName("tag_id") val id: Int,
)

@Serializable
data class AuthorDto(
    @SerialName("author_name") val name: String,
    @SerialName("author_id") val id: Int,
)
