package eu.kanade.tachiyomi.extension.all.peppercarrot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias LangsDto = Map<String, LangDto>

@Serializable
class LangDto(
    val translators: List<String>,
    @SerialName("local_name") val localName: String,
    @SerialName("iso_code") val isoCode: String,
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
    @SerialName("translated_languages") val translatedLanguages: List<String>,
)
