package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class NextData<T>(val props: Props<T>) {
    val data get() = props.pageProps
}

@Serializable
class Props<T>(val pageProps: T)


@Serializable
class MangaDetailsProps(
    @SerialName("infoDoc") val mangaDetailsDto: MangaDto,
)

@Serializable
open class Pageable<T>(
    var currentPage: Int,
    var totalPage: Int,
    val data: T,
) {
    fun hasNextPage() =
        try { (currentPage + 1) <= totalPage } catch (_: Exception) { false }
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
    val thumbnailUrl get() = "${UnionMangas.apiUrl}$_thumbnailUrl"

    val status get() = when (_status) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
