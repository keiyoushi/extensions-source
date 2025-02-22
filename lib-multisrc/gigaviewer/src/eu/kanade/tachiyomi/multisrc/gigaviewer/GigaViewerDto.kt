package eu.kanade.tachiyomi.multisrc.gigaviewer
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class GigaViewerEpisodeDto(
    val readableProduct: GigaViewerReadableProduct,
)

@Serializable
data class GigaViewerReadableProduct(
    val pageStructure: GigaViewerPageStructure,
)

@Serializable
data class GigaViewerPageStructure(
    val pages: List<GigaViewerPage> = emptyList(),
)

@Serializable
data class GigaViewerPage(
    val height: Int = 0,
    val src: String = "",
    val type: String = "",
    val width: Int = 0,
)
