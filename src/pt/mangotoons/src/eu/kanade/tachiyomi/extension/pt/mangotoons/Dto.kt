package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaListDto(
    private val pagina: Int,
    @SerialName("total_paginas") private val totalPaginas: Int,
    private val obras: List<MangaDto>,
) {
    fun toMangasPage(baseUrl: String) = MangasPage(obras.map { it.toSManga(baseUrl) }, pagina < totalPaginas)
}

@Serializable
class MangaDto(
    private val id: Int,
    private val nome: String,
    private val descricao: String? = null,
    private val imagem: String? = null,
    private val status: String? = null,
    private val tags: List<String> = emptyList(),
    private val capitulos: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = nome
        url = "/obra/$id"
        // most covers are absolute CDN URLs, a few are paths on the site
        thumbnail_url = imagem?.let { if (it.startsWith("/")) "$baseUrl$it" else it }
        description = descricao?.takeIf { it.isNotBlank() }
        genre = tags.joinToString().takeIf { it.isNotEmpty() }
        status = parseStatus(this@MangaDto.status)
    }

    fun chapterList() = capitulos
        .map { it.toSChapter(id) }
        .sortedByDescending { it.chapter_number }
}

@Serializable
class ChapterDto(
    private val numero: String,
) {
    fun toSChapter(mangaId: Int) = SChapter.create().apply {
        // same number format as the old API, so chapters already in the library keep their URL
        val parsed = numero.toFloatOrNull()
        val number = parsed?.toString()?.removeSuffix(".0") ?: numero
        name = "Capitulo $number"
        chapter_number = parsed ?: -1f
        url = "/obra/$mangaId/capitulo/$number"
        // the API sends no date (published_at is always null)
    }
}

@Serializable
class ManifestDto(
    private val pages: List<PageDto>,
) {
    fun toPageList() = pages
        .sortedBy { it.index }
        .map { Page(it.index, imageUrl = it.url) }
}

@Serializable
class PageDto(
    val index: Int,
    val url: String,
)

private fun parseStatus(status: String?) = when (status?.trim()?.lowercase()) {
    "em andamento", "lançando", "ativo" -> SManga.ONGOING
    "concluído", "finalizado" -> SManga.COMPLETED
    "hiato", "pausado" -> SManga.ON_HIATUS
    "cancelado" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
