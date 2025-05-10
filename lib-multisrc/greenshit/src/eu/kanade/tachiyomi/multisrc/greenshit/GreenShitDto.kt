package eu.kanade.tachiyomi.multisrc.greenshit

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit.Companion.CDN_URL
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

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

    fun toSChapterList(): List<SChapter> = (results as WrapperChapterDto)
        .chapters.map {
            SChapter.create().apply {
                name = it.name
                it.chapterNumber?.let {
                    chapter_number = it
                }
                url = "/capitulo/${it.id}"
                date_upload = dateFormat.tryParse(it.updateAt)
            }
        }.sortedByDescending(SChapter::chapter_number)

    fun toPageList(): List<Page> {
        val dto = (results as ChapterPageDto)

        return dto.pages.mapIndexed { index, image ->
            val imageUrl = when {
                image.isWordPressContent() -> {
                    CDN_URL.toHttpUrl().newBuilder()
                        .addPathSegments("wp-content/uploads/WP-manga/data")
                        .addPathSegments(image.src.toPathSegment())
                        .build()
                }
                else -> {
                    "$CDN_URL/scans/${dto.manga.scanId}/obras/${dto.manga.id}/capitulos/${dto.chapterNumber}/${image.src}"
                        .toHttpUrl()
                }
            }
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    private fun String.createSlug(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .trim()
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("\\p{Punct}".toRegex(), "")
            .replace("\\s+".toRegex(), "-")
            .lowercase()
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
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

/**
 * Normalizes path segments:
 * Ex: [ "/a/b/", "/a/b", "a/b/", "a/b" ]
 * Result: "a/b"
 */
private fun String.toPathSegment() = this.trim().split("/")
    .filter(String::isNotEmpty)
    .joinToString("/")
