package eu.kanade.tachiyomi.extension.es.inmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InMangaResultDto(
    val data: String?,
)

@Serializable
data class InMangaResultObjectDto<T>(
    val message: String = "",
    val success: Boolean,
    val result: List<T>,
)

@Serializable
data class InMangaChapterDto(
    @SerialName("PagesCount") val pagesCount: Int = 0,
    @SerialName("Watched") val watched: Boolean? = false,
    @SerialName("MangaIdentification") val mangaIdentification: String? = "",
    @SerialName("MangaName") val mangaName: String? = "",
    @SerialName("FriendlyMangaName") val friendlyMangaName: String? = "",
    @SerialName("Id") val id: Int? = 0,
    @SerialName("MangaId") val mangaId: Int? = 0,
    @SerialName("Number") val number: Double? = null,
    @SerialName("RegistrationDate") val registrationDate: String = "",
    @SerialName("Description") val description: String? = "",
    @SerialName("Pages") val pages: List<Int> = emptyList(),
    @SerialName("Identification") val identification: String? = "",
    @SerialName("FeaturedChapter") val featuredChapter: Boolean = false,
    @SerialName("FriendlyChapterNumber") val friendlyChapterNumber: String? = "",
    @SerialName("FriendlyChapterNumberUrl") val friendlyChapterNumberUrl: String? = "",
)
