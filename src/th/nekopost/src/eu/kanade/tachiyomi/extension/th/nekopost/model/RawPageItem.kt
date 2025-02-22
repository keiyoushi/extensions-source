package eu.kanade.tachiyomi.extension.th.nekopost.model
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class RawPageItem(
    val pageName: String? = null,
    val fileName: String? = null,
    val height: Int,
    val pageNo: Int,
    val width: Int,
)
