package eu.kanade.tachiyomi.extension.pt.azuretoons

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class AzuretoonsMangaDto(
    val title: String? = null,
    val slug: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val status: String? = null,
    val chapters: List<AzuretoonsChapterDto> = emptyList(),
)

@Serializable
data class AzuretoonsChapterDto(
    val id: Int,
    val chapterNumber: Float = 0F,
    val title: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class AzuretoonsChapterDetailDto(
    val images: List<String> = emptyList(),
)

fun AzuretoonsMangaDto.toSManga(isDetails: Boolean = false): SManga {
    val sManga = SManga.create().apply {
        title = this@toSManga.title.orEmpty()
        thumbnail_url = coverUrl?.takeIf { it.isNotBlank() } ?: ""
        initialized = isDetails
        url = "/obra/${slug.orEmpty()}"
    }
    description?.let { sManga.description = Jsoup.parseBodyFragment(it).text() }
    status?.let {
        sManga.status = when (it.lowercase()) {
            "em_lancamento", "em andamento", "ativo" -> SManga.ONGOING
            "concluido", "concluído" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
    return sManga
}

fun AzuretoonsChapterDto.toSChapter(slug: String): SChapter = SChapter.create().apply {
    name = this@toSChapter.title.orEmpty()
    chapter_number = chapterNumber
    url = "/obra/$slug/capitulo/$chapterNumber"
    date_upload = dateFormat.tryParse(createdAt)
}

fun AzuretoonsChapterDetailDto.toPageList(): List<Page> = images
    .mapIndexed { idx, imageUrl ->
        Page(idx, imageUrl = imageUrl)
    }

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
