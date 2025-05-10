package eu.kanade.tachiyomi.multisrc.greenshit

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit.Companion.CDN_URL
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup
import java.text.Normalizer

@Serializable
class ResultDto<T>(
    @SerialName("pagina")
    val currentPage: Int = 0,
    @SerialName("totalPaginas")
    val lastPage: Int = 0,
    @JsonNames("resultado")
    private val resultados: T,
) {
    val results: T get() = resultados

    fun hasNextPage() = currentPage < lastPage

    fun toSMangaList(): List<SManga> = (results as List<MangaDto>)
        .map { it.apply { slug = it.slug ?: name.createSlug() } }
        .map(MangaDto::toSManga)

    private fun String.createSlug(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .trim()
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("\\p{Punct}".toRegex(), "")
            .replace("\\s+".toRegex(), "-")
            .lowercase()
    }
}

@Serializable
class WrapperDto(
    @SerialName("dataTop")
    val popular: ResultDto<List<MangaDto>>?,
    @JsonNames("atualizacoesInicial")
    private val dataLatest: ResultDto<List<MangaDto>>?,

) {
    val latest: ResultDto<List<MangaDto>> get() = dataLatest!!
}

@Serializable
class MangaDto(
    @SerialName("obr_id")
    val id: Int,
    @SerialName("obr_descricao")
    val description: String?,
    @SerialName("obr_imagem")
    val thumbnail: String?,
    @SerialName("obr_nome")
    val name: String,
    @SerialName("obr_slug")
    var slug: String?,
    @SerialName("status")
    val status: MangaStatus,
    @SerialName("scan_id")
    val scanId: Int,
    @SerialName("tags")
    val genres: List<Genre>,
) {

    fun toSManga(): SManga {
        val sManga = SManga.create().apply {
            title = name
            thumbnail_url = thumbnail?.let {
                when {
                    it.startsWith("http") -> thumbnail
                    else -> "$CDN_URL/scans/$scanId/obras/${this@MangaDto.id}/$thumbnail"
                }
            }
            initialized = true
            url = "/obra/${this@MangaDto.id}/${this@MangaDto.slug}"
            genre = genres.joinToString()
        }

        description?.let { Jsoup.parseBodyFragment(it).let { sManga.description = it.text() } }
        sManga.status = status.toStatus()

        return sManga
    }

    @Serializable
    class Genre(
        @SerialName("tag_nome")
        val value: String,
    ) {
        override fun toString(): String = value
    }

    @Serializable
    class MangaStatus(
        @SerialName("stt_nome")
        val value: String?,
    ) {
        fun toStatus(): Int {
            return when (value?.lowercase()) {
                "em andamento" -> SManga.ONGOING
                "completo" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ChapterDto(
    @SerialName("cap_id")
    val id: Int,
    @SerialName("cap_nome")
    val name: String,
    @SerialName("cap_numero")
    val chapterNumber: Float?,
    @SerialName("cap_lancado_em")
    val updateAt: String,
)

@Serializable
class WrapperChapterDto(
    @SerialName("capitulos")
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterPageDto(
    @SerialName("cap_paginas")
    val pages: List<PageDto>,
    @SerialName("obra")
    val manga: MangaReferenceDto,
    @SerialName("cap_numero")
    val chapterNumber: Int,
) {
    @Serializable
    class MangaReferenceDto(
        @SerialName("obr_id")
        val id: Int,
        @SerialName("scan_id")
        val scanId: Int,
    )
}

@Serializable
class PageDto(
    val src: String,
    @SerialName("numero")
    val number: Int? = null,
) {
    fun isWordPressContent(): Boolean = number == null
}
