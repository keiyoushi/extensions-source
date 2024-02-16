package eu.kanade.tachiyomi.extension.en.mangahot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaListDto(
    val data: ListDataDto,
) {
    @Serializable
    data class ListDataDto(
        val listManga: List<EntryDto>,
        val total: Int? = null,
    )
}

@Serializable
data class EntryDto(
    val name: String,
    val webUrl: String,
    val thumbUrl: String,
)

@Serializable
data class RequestBodyDto(
    val page: Int,
)

@Serializable
data class ChapterDto(
    val idx: String,
    @SerialName("name") val chapterName: String,
)

@Serializable
data class PagesDto(
    val data: DataDto,
) {
    @Serializable
    data class DataDto(
        val chapter: ChapterPagesDto,
    ) {
        @Serializable
        data class ChapterPagesDto(
            val resources: List<String>,
            @SerialName("resource_storage") val cdnHost: String,
        )
    }
}
