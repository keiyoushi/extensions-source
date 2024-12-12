package eu.kanade.tachiyomi.extension.en.kunmangato

import kotlinx.serialization.Serializable

@Serializable
class KunMangaToChapterResourcesDto(
    val status: Int,
    val data: KunMangaToDataDto,
)

@Serializable
class KunMangaToDataDto(
    val resources: List<KunMangaToResourceDto>,
)

@Serializable
class KunMangaToResourceDto(
    val id: Int,
    val name: Int,
    val thumb: String,
)
