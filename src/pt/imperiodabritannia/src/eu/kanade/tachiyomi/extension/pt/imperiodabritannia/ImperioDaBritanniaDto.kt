package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Pageable(
    @SerialName("obras")
    val content: List<MangaDto>,
    private val pagination: Pagination,
) {
    fun hasNextPage() = pagination.hasNextPage
}

@Serializable
class Pagination(
    val hasNextPage: Boolean,
)

@Serializable
class MangaDto(
    private val id: Long,
    @SerialName("nome")
    private val name: String,
    private val slug: String?,
    @SerialName("descricao")
    private val description: String?,
    @SerialName("imagem")
    private val thumbnail: String?,
    @SerialName("status_nome")
    private val status: String,
    @SerialName("tags")
    private val genres: List<Value>,
    @SerialName("capitulos")
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSlug() = "/manga/${slug ?: id}"

    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = name
        description = this@MangaDto.description
        genre = genres.joinToString { it.name }
        status = when (this@MangaDto.status) {
            "Ativo" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        initialized = true
        thumbnail_url = "https://cdn.${baseUrl.substringAfterLast("/")}/$thumbnail"
        url = toSlug()
    }
}

@Serializable
class Value(
    @SerialName("nome")
    val name: String,
)

@Serializable
class MangaDetailsDto(
    @SerialName("obra")
    private val content: MangaDto,
) {
    fun toSManga(baseUrl: String) = content.toSManga(baseUrl)
    fun toSChapterList() = content.chapters.map { it.toSChapter() }.reversed()
}

@Serializable
class ChapterDto(
    @SerialName("numero")
    private val number: String,
    @SerialName("obra_id")
    private val mangaId: Long,
    @SerialName("paginas")
    val pages: List<ImageSrc> = emptyList(),
) {
    fun toSChapter() = SChapter.create().apply {
        val number = numberNormalized()
        name = "Capítulo $number"
        url = "/manga/$mangaId/capitulo/$number"
    }

    private fun numberNormalized(): Number = this@ChapterDto.number.takeIf { it.isInteger() }?.toFloat()?.toInt() ?: this@ChapterDto.number.toFloat()

    fun String.isInteger(): Boolean {
        val number = toFloatOrNull() ?: return false
        return number % 1.0 == 0.0
    }
}

@Serializable
class PageDto(
    @SerialName("capitulo")
    val chapter: ChapterDto,
) {
    fun toPageList() = chapter.pages.mapIndexed { index, image ->
        Page(index, imageUrl = image.url)
    }
}

@Serializable
class ImageSrc(
    @SerialName("cdn_id")
    val url: String,
)
