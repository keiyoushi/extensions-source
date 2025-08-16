package eu.kanade.tachiyomi.extension.pt.manhastro

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class ResponseWrapper<T>(
    val data: T,
)

@Serializable
class PopularMangaDto(
    @SerialName("manga_id")
    val id: Int,
)

@Serializable
class MangaDto(
    @SerialName("manga_id")
    val id: Int,
    @SerialName("titulo")
    val title: String,
    @SerialName("titulo_brasil")
    val titleLocalized: String?,
    @SerialName("descricao")
    val description: String,
    @SerialName("imagem")
    val thumbnailUrl: String?,
    @SerialName("generos")
    val genres: String?,
) {

    val slug: String = title.replace(" ", "-")
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        description = """
            ${this@MangaDto.description}

            Nome alternativo: $titleLocalized
        """.trimIndent()
        thumbnail_url = "https://$thumbnailUrl"
        url = this@MangaDto.id.toString()
        genre = this@MangaDto.genres?.parseAs<List<String>>()?.joinToString()
        initialized = true
    }
}

@Serializable
class ChapterDto(
    @SerialName("capitulo_id")
    val id: Int,
    @SerialName("capitulo_nome")
    val name: String,

    @SerialName("capitulo_data")
    val date: String,
) {
    fun toSChapter(mangaId: Int) = SChapter.create().apply {
        name = this@ChapterDto.name
        date_upload = dateFormat.tryParse(date)
        NUMBER_REGEX.find(name)?.groupValues?.firstOrNull()?.let {
            chapter_number = it.toFloat()
        }
        url = "$mangaId:${this@ChapterDto.id}"
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        private val NUMBER_REGEX = """\d+(?:\.\d+)?""".toRegex()
    }
}

@Serializable
class PageDto(
    val chapter: ItemDto,
) {
    @Serializable
    class ItemDto(
        @SerialName("baseUrl")
        val url: String,
        @SerialName("hash")
        val pathSegment: String,
        @SerialName("data")
        val images: List<String>,
    )

    fun toPageList(): List<Page> {
        return with(chapter) {
            images.mapIndexed { index, image ->
                Page(index, imageUrl = "$url/$pathSegment/$image")
            }
        }
    }
}
