package eu.kanade.tachiyomi.extension.pt.azuretoons

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class AzuretoonsMangaDto(
    val title: String,
    val slug: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val status: String? = null,
    val chapters: List<AzuretoonsChapterDto> = emptyList(),
    val viewCount: Int = 0,
)

@Serializable
class AzuretoonsChapterDto(
    val chapterNumber: Float = 0F,
    val title: String? = null,
    val createdAt: String? = null,
)

@Serializable
class AzuretoonsChapterDetailDto(
    val images: List<String> = emptyList(),
)

fun AzuretoonsMangaDto.toSManga(): SManga {
    val sManga = SManga.create().apply {
        title = this@toSManga.title
        thumbnail_url = coverUrl?.takeIf { it.isNotBlank() } ?: ""
        url = "/obra/$slug"
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
    name = this@toSChapter.title!!
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
