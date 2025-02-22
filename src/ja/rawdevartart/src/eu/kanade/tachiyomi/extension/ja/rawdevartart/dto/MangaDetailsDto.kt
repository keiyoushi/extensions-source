package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class MangaDetailsDto(
    val detail: MangaDto,
    val tags: List<TagDto>,
    val authors: List<AuthorDto>,
    val chapters: List<ChapterDto>,
)
