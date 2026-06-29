package eu.kanade.tachiyomi.extension.all.peppercarrot

import kotlinx.serialization.Serializable

typealias LangsDto = Map<String, LangDto>

@Serializable
class LangDto(
    val translators: List<String>,
    val local_name: String,
    val iso_code: String,
)

// ProtoBuf: should not change field type and order
@Serializable
class LangData(
    val key: String,
    val name: String,
    val progress: String,
    val translators: String,
    val title: String?,
)

class Lang(
    val key: String,
    val name: String,
    val code: String,
    val translators: String,
    val translatedCount: Int,
)

@Serializable
class EpisodeDto(
    val translated_languages: List<String>,
)
