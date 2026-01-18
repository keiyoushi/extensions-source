package eu.kanade.tachiyomi.multisrc.greenshit

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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
    @SerialName("obras")
    @JsonNames("resultado", "resultados")
    private val obras: T,
) {
    val results: T get() = obras

    fun hasNextPage() = currentPage < lastPage

    companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}

fun ResultDto<List<MangaDto>>.toSMangaList(cdnUrl: String, useWidth: Boolean, includeSlug: Boolean = true, defaultScanId: Int? = null): List<SManga> =
    results.filter { it.type != "TEXTO" }.map { it.toSManga(cdnUrl, useWidth, includeSlug, defaultScanId) }

@Serializable
class MangaDto(
    @JsonNames("obr_id", "id")
    val id: Int,
    @JsonNames("obr_nome", "name")
    val name: String,
    @JsonNames("obr_slug", "slug")
    var slug: String? = null,
    @JsonNames("obr_imagem", "image", "thumbnail")
    val thumbnail: String? = null,
    @JsonNames("obr_descricao", "description")
    val description: String? = null,
    @SerialName("scan_id")
    val scanId: Int,
    @JsonNames("status", "obr_status")
    val status: MangaStatus? = null,
    @JsonNames("tags", "genres")
    val genres: List<Genre> = emptyList(),
    @JsonNames("capitulos", "chapters")
    val chapters: List<ChapterDto>? = null,
    val type: String? = null,
) {
    fun toSManga(cdnUrl: String, useWidth: Boolean, includeSlug: Boolean = false, defaultScanId: Int? = null) = SManga.create().apply {
        title = name
        val finalSlug = slug?.takeIf { it.isNotEmpty() } ?: name?.toSlug()
        url = if (includeSlug) {
            "/obra/$id/$finalSlug"
        } else {
            "/obra/$id"
        }
        thumbnail_url = buildThumbnailUrl(cdnUrl, useWidth, defaultScanId)
        genre = genres.joinToString()
        description = this@MangaDto.description?.takeIf { it.isNotBlank() }?.let { Jsoup.parseBodyFragment(it).text() }
        status = this@MangaDto.status?.toStatus() ?: SManga.UNKNOWN
        initialized = false
    }

    private fun buildThumbnailUrl(cdnUrl: String, useWidth: Boolean, defaultScanId: Int?): String? = thumbnail?.let {
        when {
            it.startsWith("http") -> it
            it.contains("/") -> "$cdnUrl/$it"
            else -> {
                val width = if (useWidth) "?width=300" else ""
                val scanId = scanId ?: defaultScanId
                val mangaId = id
                "$cdnUrl/scans/$scanId/obras/$mangaId/$it$width"
            }
        }
    }

    fun toSChapterList(): List<SChapter> =
        chapters?.map { it.toSChapter() }?.sortedByDescending { it.chapter_number } ?: emptyList()

    @Serializable
    class Genre(@JsonNames("tag_nome", "name") val value: String) {
        override fun toString() = value
    }

    @Serializable(with = MangaStatus.Serializer::class)
    class MangaStatus(val value: String?) {
        fun toStatus(): Int = when (value?.lowercase()) {
            "em andamento" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        object Serializer : KSerializer<MangaStatus> {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("MangaStatus", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): MangaStatus {
                val jsonDecoder = decoder as? JsonDecoder ?: return MangaStatus(decoder.decodeString())
                val element = jsonDecoder.decodeJsonElement()

                val value = when (element) {
                    is JsonPrimitive -> element.content
                    is JsonObject -> listOf("stt_nome", "value", "name")
                        .firstNotNullOfOrNull { key -> element[key]?.jsonPrimitive?.content }
                    else -> null
                }
                return MangaStatus(value)
            }

            override fun serialize(encoder: Encoder, value: MangaStatus) {
                encoder.encodeString(value.value ?: "")
            }
        }
    }
}

@Serializable
class ChapterDto(
    @JsonNames("cap_id", "id")
    val id: Int,
    @JsonNames("cap_nome", "name")
    val name: String,
    @JsonNames("cap_numero", "number")
    val numero: Float? = null,
    @JsonNames("cap_lancado_em", "cap_liberar_em", "cap_criado_em")
    val releaseDate: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        this.name = this@ChapterDto.name
        url = "/capitulo/$id"
        numero?.let { chapter_number = it }
        date_upload = ResultDto.DATE_FORMAT.tryParse(releaseDate)
    }
}

@Serializable
class FiltersDto(
    @SerialName("generos") val genres: List<Genre> = emptyList(),
    @SerialName("formatos") val formats: List<Format> = emptyList(),
    @SerialName("status") val statuses: List<Status> = emptyList(),
    @SerialName("tags") val tags: List<Tag> = emptyList(),
)

@Serializable
class Genre(
    @JsonNames("gen_id", "id")
    val id: Int,
    @JsonNames("gen_nome", "name")
    val name: String,
)

@Serializable
class Format(
    @JsonNames("formt_id", "id")
    val id: Int,
    @JsonNames("formt_nome", "name")
    val name: String,
)

@Serializable
class Status(
    @JsonNames("stt_id", "id")
    val id: Int,
    @JsonNames("stt_nome", "name")
    val name: String,
)

@Serializable
class Tag(
    @JsonNames("tag_id", "id")
    val id: Int,
    @JsonNames("tag_nome", "name")
    val name: String,
)

@Serializable
class ChapterPagesDto(
    @SerialName("obr_id") val mangaId: Int? = null,
    @SerialName("cap_nome") val name: String = "",
    @SerialName("cap_numero") private val _chapterNumber: JsonPrimitive? = null,
    @SerialName("cap_paginas") val pages: List<PageDto> = emptyList(),
    @SerialName("cap_texto") val text: String? = null,
    @SerialName("cap_tipo") val type: String = "IMAGEM",
) {
    val chapterNumber: String? get() = _chapterNumber?.content

    fun toPageList(cdnUrl: String): List<Page> {
        if (type == "TEXTO") {
            throw IllegalArgumentException("Novels are not supported")
        }

        return pages.mapIndexedNotNull { index, pageDto ->
            pageDto.toPageOrNull(cdnUrl, mangaId, chapterNumber)?.let { Page(index, it.url, it.imageUrl) }
        }
    }
}

@Serializable
class PageDto(
    val src: String,
    val mime: String? = null,
    var path: String? = null,
    @SerialName("numero") private val _numero: JsonPrimitive? = null,
) {
    val numero: String? get() = _numero?.content

    fun toPageOrNull(cdnUrl: String, mangaId: Int?, chapterNumber: String?): Page? = runCatching { toPage(cdnUrl, mangaId, chapterNumber) }.getOrNull()

    fun toPage(cdnUrl: String, mangaId: Int?, chapterNumber: String?): Page {
        val cleanSrc = src.trim()
        require(cleanSrc.isNotBlank()) { "Page src cannot be empty" }

        if (cleanSrc.startsWith("http://") || cleanSrc.startsWith("https://")) {
            return Page(0, imageUrl = cleanSrc)
        }

        val normalizedSrc = cleanSrc.removePrefix("/")

        val imageUrl = when {
            mime != null -> "$cdnUrl/wp-content/uploads/WP-manga/data/$normalizedSrc"
            path != null -> {
                val cleanPath = path!!.trim()
                val absolutePath = if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"
                if (absolutePath.endsWith(normalizedSrc, ignoreCase = true)) {
                    "$cdnUrl$absolutePath"
                } else {
                    val pathWithoutTrailing = absolutePath.removeSuffix("/")
                    "$cdnUrl$pathWithoutTrailing/$normalizedSrc"
                }
            }
            else -> "$cdnUrl/scans/1/obras/${mangaId ?: 0}/capitulos/$chapterNumber/$normalizedSrc"
        }

        return Page(0, imageUrl = imageUrl)
    }
}

private val DiacriticsRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
private val NonAlfaRegex = "[^a-z0-9\\s-]".toRegex()
private val WhitespaceRegex = "\\s+".toRegex()

fun String?.toSlug(slugSeparator: String = "-"): String {
    if (this == null) return ""
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(DiacriticsRegex, "")
        .lowercase()
        .replace(NonAlfaRegex, "")
        .replace(WhitespaceRegex, slugSeparator)
        .trim(*slugSeparator.toCharArray())
}
