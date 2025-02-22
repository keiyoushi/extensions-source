package eu.kanade.tachiyomi.extension.fr.flamescansfr
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    val cover: String?,
    val slug: String,
    val title: String,
)

@Serializable
class SearchDto(
    val comics: List<MangaDto>,
)

@Serializable
class SearchQueryDto(
    val results: List<MangaDto>,
)
