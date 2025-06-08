package eu.kanade.tachiyomi.extension.pt.geasscomics

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Pagination(
    @SerialName("has_next")
    val hasNext: Boolean = false,
)

@Serializable
class PopularDto(
    @SerialName("obras")
    val mangas: List<MangaDto> = emptyList(),
    val pagination: Pagination = Pagination(),
) {
    fun hasNextPage() = pagination.hasNext
}

@Serializable
class LatestDto(
    @SerialName("recentes")
    val mangas: List<SimpleMangaDto> = emptyList(),
)

@Serializable
class DetailsDto(
    @SerialName("obra")
    val manga: MangaDto,
) {
    fun toChapters(): List<SChapter> {
        return manga.chapters.map {
            SChapter.create().apply {
                name = it.name
                chapter_number = it.number.toFloat()
                url = "/manga/${manga.id}/chapter/${it.number}"
            }
        }.sortedBy(SChapter::chapter_number).reversed()
    }
}

@Serializable
class SimpleMangaDto(
    @SerialName("obra_id")
    val id: Int,
    @SerialName("titulo_obra")
    val title: String,
    @SerialName("capa")
    val thumbnail_url: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@SimpleMangaDto.title
        thumbnail_url = this@SimpleMangaDto.thumbnail_url
        url = this@SimpleMangaDto.id.toString()
    }
}

@Serializable
class MangaDto(
    val id: Int,
    @SerialName("titulo")
    val title: String,
    @SerialName("artista")
    val artist: String,
    @SerialName("autor")
    val author: String,
    @SerialName("capa")
    val thumbnail_url: String,
    @SerialName("generos")
    val genres: List<Genre>,
    val sinopse: String,
    val status: String,
    @SerialName("capitulos")
    val chapters: List<ChapterDto> = emptyList(),
) {

    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        description = this@MangaDto.sinopse
        thumbnail_url = this@MangaDto.thumbnail_url
        artist = this@MangaDto.artist
        author = this@MangaDto.author
        genre = genres.joinToString { it.name }
        url = this@MangaDto.id.toString()
        status = when (this@MangaDto.status) {
            "EM ANDAMENTO" -> SManga.ONGOING
            "COMPLETO" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    @Serializable
    class Genre(
        @SerialName("nome")
        val name: String,
    )
}

@Serializable
class ChapterDto(
    val id: Long,
    @SerialName("numero")
    val number: String,
    @SerialName("titulo")
    val name: String,
)

@Serializable
class PageDto(
    @SerialName("capitulo")
    val chapter: Image,
) {
    fun toPages(): List<Page> =
        chapter.images.mapIndexed { index, source -> Page(index, imageUrl = source.url) }

    @Serializable
    class Source(
        val url: String,
    )

    @Serializable
    class Image(
        @SerialName("imagens")
        val images: List<Source> = emptyList(),
    )
}
