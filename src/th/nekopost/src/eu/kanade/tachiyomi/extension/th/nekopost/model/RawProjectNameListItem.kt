package eu.kanade.tachiyomi.extension.th.nekopost.model
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProjectNameListItem(
    @SerialName("np_name")
    val npName: String,
    @SerialName("np_name_link")
    val npNameLink: String,
    @SerialName("np_no_chapter")
    val npNoChapter: String,
    @SerialName("np_project_id")
    val npProjectId: String,
    @SerialName("np_status")
    val npStatus: String,
    @SerialName("np_type")
    val npType: String,
)
