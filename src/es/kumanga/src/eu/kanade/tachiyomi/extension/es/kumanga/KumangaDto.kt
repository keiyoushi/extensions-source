package eu.kanade.tachiyomi.extension.es.kumanga

import kotlinx.serialization.Serializable

@Serializable
class KumangaImageDto(
    val imgURL: String? = null,
)

@Serializable
class KumangaOtherChapterDto(
    val NumCap: String? = null,
    val title: String? = null,
)
