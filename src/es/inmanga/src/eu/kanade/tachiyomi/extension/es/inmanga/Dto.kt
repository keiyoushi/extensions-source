package eu.kanade.tachiyomi.extension.es.inmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class InMangaResultDto(
    val data: String? = null,
)

@Serializable
class InMangaResultObjectDto<T>(
    val success: Boolean,
    val result: List<T> = emptyList(),
)

@Serializable
class InMangaChapterDto(
    @SerialName("Number") val number: Double? = null,
    @SerialName("RegistrationDate") val registrationDate: String = "",
    @SerialName("Identification") val identification: String? = "",
    @SerialName("FriendlyChapterNumber") val friendlyChapterNumber: String? = "",
)
