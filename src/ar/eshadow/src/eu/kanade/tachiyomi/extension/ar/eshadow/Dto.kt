package eu.kanade.tachiyomi.extension.ar.eshadow

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaList(
    private val data: List<Manga>,
    private val limit: Int,
    private val page: Int,
    private val total: Int,
) {
    fun toMangasPage() = MangasPage(data.map { it.toSManga() }, page * limit < total)
}

@Serializable
class Manga(
    private val id: String,
    private val slug: String,
    private val title: String,
    private val description: String? = null,
    private val coverImage: String? = null,
    private val author: String? = null,
    private val status: String? = null,
    val chapters: List<Chapter> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@Manga.title
        thumbnail_url = coverImage
        description = this@Manga.description
        author = author
        status = when (this@Manga.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject {
            put("slug", slug)
        }
    }
}

@Serializable
class Chapter(
    private val id: String? = null,
    private val mangaId: String = "",
    private val title: String? = null,
    private val number: Int,
    private val images: List<String> = emptyList(),
    private val publishedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id!!
        name = buildString {
            append("الفصل ", number, title?.let { " - $it" } ?: "")
        }
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(publishedAt)
        memo = buildJsonObject {
            put("pages", images.toJsonElement())
        }
    }
}
