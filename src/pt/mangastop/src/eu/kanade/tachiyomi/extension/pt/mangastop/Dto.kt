package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
internal class MangaListDto(
    private val mangas: List<MangaDto>,
    private val paginacao: PaginacaoDto? = null,
    private val pagina: Int? = null,
    @SerialName("total_paginas") private val totalPaginas: Int? = null,
) {
    fun toMangasPage(): MangasPage {
        val hasNextPage = paginacao?.temProxima ?: ((pagina ?: 0) < (totalPaginas ?: 0))
        return MangasPage(mangas.map { it.toSManga() }, hasNextPage)
    }
}

@Serializable
internal class PaginacaoDto(
    @SerialName("tem_proxima") val temProxima: Boolean,
)

@Serializable
internal class BuscaDto(
    private val pagina: Int,
    private val obras: BuscaObrasDto,
) {
    fun toMangasPage() = MangasPage(obras.lista.map { it.toSManga() }, pagina < obras.totalPaginas)
}

@Serializable
internal class BuscaObrasDto(
    val lista: List<MangaDto>,
    @SerialName("total_paginas") val totalPaginas: Int,
)

@Serializable
internal class MangaDto(
    private val id: Int,
    private val titulo: String,
    private val thumbnail: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = titulo
        thumbnail_url = thumbnail?.takeIf { it.isNotEmpty() }
    }
}

@Serializable
internal class ObraDto(
    private val id: Int,
    private val titulo: String,
    private val alternativo: String? = null,
    private val sinopse: String? = null,
    @SerialName("capa_url") private val capaUrl: String? = null,
    private val status: String? = null,
    private val tipo: String? = null,
    private val autor: String? = null,
    private val artista: String? = null,
    private val generos: List<NomeDto> = emptyList(),
    @SerialName("manga_autor") private val mangaAutor: List<NomeDto> = emptyList(),
    @SerialName("manga_artista") private val mangaArtista: List<NomeDto> = emptyList(),
    private val capitulos: List<CapituloDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = titulo
        thumbnail_url = capaUrl?.takeIf { it.isNotEmpty() }
        author = mangaAutor.joinToString { it.nome }.ifEmpty { autor }?.takeIf { it.isNotBlank() }
        artist = mangaArtista.joinToString { it.nome }.ifEmpty { artista }?.takeIf { it.isNotBlank() }
        description = buildString {
            sinopse?.let { append(Jsoup.parseBodyFragment(it).wholeText().trim()) }
            if (!alternativo.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Títulos alternativos: ")
                append(alternativo)
            }
        }.ifEmpty { null }
        genre = (generos.map { it.nome } + listOfNotNull(tipo)).joinToString()
        status = when (this@ObraDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject { put("id", id.toString()) }
        initialized = true
    }

    fun toSChapterList() = capitulos.map { it.toSChapter() }.sortedByDescending { it.chapter_number }
}

@Serializable
internal class NomeDto(
    val nome: String,
)

@Serializable
internal class CapituloDto(
    private val id: Int,
    private val numero: String? = null,
    @SerialName("titulo_post") private val tituloPost: String,
    @SerialName("data_publicacao") private val dataPublicacao: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        val number = numero?.replace(',', '.')?.toFloatOrNull()
        url = id.toString()
        name = number?.let { "Capítulo ${it.toString().removeSuffix(".0")}" } ?: tituloPost
        chapter_number = number ?: -1f
        date_upload = DATE_FORMAT.tryParseDate(dataPublicacao, SAO_PAULO)
    }
}

@Serializable
internal class LeitorDto(
    @SerialName("manga_id") val mangaId: Int,
    val imagens: List<ImagemDto>,
)

@Serializable
internal class ImagemDto(
    val url: String,
)

@Serializable
internal class PostIdDto(
    val id: Int,
)

@Serializable
internal class GenreDto(
    val name: String,
    val slug: String,
)

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
private val SAO_PAULO = ZoneId.of("America/Sao_Paulo")
