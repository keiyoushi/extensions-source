package eu.kanade.tachiyomi.extension.all.mangahosted

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDetailsDto(private val data: Props) {
    val details: MangaDto get() = data.details

    @Serializable
    class Props(
        @SerialName("infoDoc") val details: MangaDto,
    )
}

@Serializable
open class Pageable<T>(
    var currentPage: Int,
    var totalPage: Int,
    val data: List<T>,
) {
    fun hasNextPage() = (currentPage + 1) <= totalPage
}

@Serializable
class ChapterDto(
    val date: String,
    @SerialName("idDoc") val slugManga: String,
    @SerialName("idDetail") val id: String,
    @SerialName("nameChapter") val name: String,
) {
    fun toChapterUrl(lang: String) = "/$lang/${this.slugManga}/$id"
}

@Serializable
class MangaDto(
    @SerialName("name") val title: String,
    @SerialName("image") private val _thumbnailUrl: String,
    @SerialName("idDoc") val slug: String,
    @SerialName("genresName") val genres: String,
    @SerialName("status") val _status: String,
) {
    val thumbnailUrl get() = "${MangaHosted.baseApiUrl}$_thumbnailUrl"

    val status get() = when (_status) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class SearchDto(
    @SerialName("data")
    val mangas: List<MangaDto>,
)

@Serializable
class PageDto(val `data`: Data) {
    val pages: List<String> get() = `data`.detailDocuments.source.split("#")

    @Serializable
    class Data(@SerialName("detail_documents") val detailDocuments: DetailDocuments)

    @Serializable
    class DetailDocuments(val source: String)
}
