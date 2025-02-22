package eu.kanade.tachiyomi.extension.vi.goctruyentranh
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
class SearchDTO(
    val comics: Comics,
)

@Serializable
class Data(
    var name: String,
    val slug: String,
    val thumbnail: String?,
)

@Serializable
class Comics(
    val current_page: Int,
    val data: ArrayList<Data> = arrayListOf(),
    val last_page: Int,
)
