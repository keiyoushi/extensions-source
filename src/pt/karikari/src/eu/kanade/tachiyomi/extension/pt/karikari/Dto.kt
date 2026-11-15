package eu.kanade.tachiyomi.extension.pt.karikari

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class WorkDto(
    private val slug: String,
    private val titulo: String,
    @SerialName("capa_vertical_path") private val capaVerticalPath: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$slug"
        title = titulo
        thumbnail_url = capaVerticalPath
    }
}

// The work page without its chapters, since a novel's text chapters have no slug
@Serializable
class WorkInfoDto(
    val obra: WorkDetailsDto,
    val ehNovel: Boolean,
)

@Serializable
class WorkPageDto(
    val obra: WorkDetailsDto,
    private val capitulos: List<ChapterDto>,
    private val capitulosPrecos: Map<String, Int>,
) {
    fun toSChapterList(): List<SChapter> {
        // Like the site's Portuguese page: a work in both languages only shows its Portuguese chapters
        val bilingual = obra.idioma == "pt_en" && capitulos.any { it.language == "pt" } && capitulos.any { it.language == "en" }
        val now = System.currentTimeMillis()
        // The site numbers a chapter by its place in the whole list, other languages and scheduled chapters included
        return capitulos.mapIndexedNotNull { index, chapter ->
            chapter.takeIf { (!bilingual || it.language == "pt") && !it.isScheduledAfter(now) }
                ?.toSChapter(index + 1, locked = chapter.isForSupporters || (capitulosPrecos[chapter.id] ?: 0) > 0)
        }.reversed()
    }
}

@Serializable
class WorkDetailsDto(
    private val slug: String,
    private val titulo: String,
    @SerialName("titulo_en") private val tituloEn: String? = null,
    private val descricao: String? = null,
    private val status: String? = null,
    @SerialName("capa_vertical_path") private val capaVerticalPath: String? = null,
    val idioma: String? = null,
    @SerialName("genero_principal") private val generoPrincipal: GenreDto? = null,
    @SerialName("genero_secundario") private val generoSecundario: GenreDto? = null,
    private val usuarios: AuthorDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/obra/$slug"
        title = titulo
        thumbnail_url = capaVerticalPath
        author = usuarios?.nome
        description = MarkdownDescription.Builder()
            .description(descricao)
            .altTitles(listOfNotNull(tituloEn))
            .build()
        genre = listOfNotNull(generoPrincipal?.nome, generoSecundario?.nome).joinToString().ifEmpty { null }
        status = when (this@WorkDetailsDto.status) {
            "Em produção" -> SManga.ONGOING
            "Finalizado" -> SManga.COMPLETED
            "Hiato" -> SManga.ON_HIATUS
            "Cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GenreDto(val nome: String)

@Serializable
class AuthorDto(val nome: String)

@Serializable
class ChapterDto(
    val id: String,
    private val slug: String,
    private val titulo: String? = null,
    private val idioma: String? = null,
    @SerialName("apenas_membros") private val apenasMembros: Boolean? = null,
    @SerialName("exclusivo_apoia_campanha_id") private val exclusivoApoiaCampanhaId: String? = null,
    @SerialName("publicacao_agendada") private val publicacaoAgendada: Boolean? = null,
    @SerialName("data_de_lancamento") private val dataDeLancamento: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    val language get() = idioma ?: "pt"

    val isForSupporters get() = apenasMembros == true || exclusivoApoiaCampanhaId != null

    fun isScheduledAfter(time: Long) = publicacaoAgendada == true && Instant.tryParse(dataDeLancamento) > time

    // Locked chapters are the ones the site sells for its coins or keeps for members and supporters
    fun toSChapter(number: Int, locked: Boolean) = SChapter.create().apply {
        url = "/ler/$slug"
        name = (if (locked) "🔒 " else "") + listOfNotNull("Capítulo $number", titulo?.takeIf { it.isNotBlank() }).joinToString(": ")
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(dataDeLancamento ?: createdAt)
    }
}

@Serializable
class ReaderDto(private val capitulo: ReaderChapterDto) {
    val pages get() = capitulo.capitulosPaginas.sortedBy { it.ordem }.map { it.imagemPath }
}

@Serializable
class ReaderChapterDto(
    @SerialName("capitulos_paginas") val capitulosPaginas: List<PageDto>,
)

@Serializable
class PageDto(
    val ordem: Int,
    @SerialName("imagem_path") val imagemPath: String,
)
