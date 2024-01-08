package eu.kanade.tachiyomi.extension.en.mangamiso

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MisoNewMangaPage(
    val newManga: List<MisoManga> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MisoLatestUpdatesPage(
    val newManga: List<MisoManga> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MisoBrowseManga(
    val foundList: List<MisoManga> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MisoManga(

    val title: String = "",

    val description: String = "",

    val pathName: String = "",

    val status: String = "",

    val coverImage: String = "",

    val author: List<String> = emptyList(),
    val artist: List<String> = emptyList(),

    val demographic: List<String> = emptyList(),

    val genre: List<String> = emptyList(),

    val themes: List<String> = emptyList(),

    val contentType: List<String> = emptyList(),

    val contentWarning: List<String> = emptyList(),

    val glory: List<String> = emptyList(),
) {
    val tags: List<String> get() {
        return demographic + genre + themes + contentType + contentWarning + glory
    }
}

@Serializable
data class MisoChapterList(
    val chapters: List<MisoChapter>,
)

@Serializable
data class MisoChapter(

    @SerialName("chapterTitle") val title: String = "",

    val pathName: String = "",

    val chapterNum: Float = 1f,

    val createdAt: String = "",

)

@Serializable
data class MisoPageList(
    val pages: List<MisoPage>,
)

@Serializable
data class MisoPage(
    val path: String,
)
