package eu.kanade.tachiyomi.extension.all.mangadex.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

@Serializable
data class AggregateDto(
    val result: String,
    val volumes: Map<String, AggregateVolume>?,
)

@Serializable
data class AggregateVolume(
    val volume: String,
    val count: String,
    val chapters: Map<String, AggregateChapter>,
)

@Serializable
data class AggregateChapter(
    val chapter: String,
    val count: String,
)
