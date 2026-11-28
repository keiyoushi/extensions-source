package eu.kanade.tachiyomi.multisrc.rawspa

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class MangaListDto(
    @SerialName("manga_list") private val mangaList: List<MangaDto>,
    private val pagi: PagiDto,
) {
    fun toMangasPage() = MangasPage(mangaList.map { it.toSManga() }, pagi.button.next > 0)
}

@Serializable
class MangaDto(
    @SerialName("manga_id") private val mangaId: Int,
    @SerialName("manga_name") private val mangaName: String,
    @SerialName("manga_cover_img") private val mangaCoverImg: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = mangaId.toString()
        title = mangaName
        thumbnail_url = mangaCoverImg
    }
}

@Serializable
class PagiDto(val button: ButtonDto)

@Serializable
class ButtonDto(val next: Int)

@Serializable
class MangaDetailResponseDto(
    private val detail: MangaDetailDto,
    private val authors: List<AuthorDto>,
    private val tags: List<TagDto>,
    val chapters: List<ChapterDto>,
) {
    val mangaId get() = detail.mangaId

    fun toSManga() = SManga.create().apply {
        url = detail.mangaId.toString()
        title = detail.mangaName
        thumbnail_url = detail.mangaCoverImgFull ?: detail.mangaCoverImg
        author = authors.joinToString { it.authorName }.ifEmpty { null }
        genre = tags.joinToString { it.tagName.toTitleCase() }.ifEmpty { null }
        status = if (detail.mangaStatus) SManga.COMPLETED else SManga.ONGOING
        description = MarkdownDescription.Builder()
            .description(detail.mangaDescription)
            .altTitles(detail.mangaOthersName.orEmpty().split(',', ';'))
            .build()
    }
}

@Serializable
class MangaDetailDto(
    @SerialName("manga_id") val mangaId: Int,
    @SerialName("manga_name") val mangaName: String,
    @SerialName("manga_others_name") val mangaOthersName: String?,
    @SerialName("manga_status") val mangaStatus: Boolean,
    @SerialName("manga_description") val mangaDescription: String?,
    @SerialName("manga_cover_img") val mangaCoverImg: String?,
    @SerialName("manga_cover_img_full") val mangaCoverImgFull: String?,
)

@Serializable
class AuthorDto(@SerialName("author_name") val authorName: String)

@Serializable
class TagDto(@SerialName("tag_name") val tagName: String)

@Serializable
class ChapterDto(
    @SerialName("chapter_title") private val chapterTitle: String?,
    @SerialName("chapter_number") private val chapterNumber: Float,
    @SerialName("chapter_date_published") private val chapterDatePublished: String?,
) {
    val number get() = chapterNumber.toString().removeSuffix(".0")

    fun toSChapter(chapterUrl: String) = SChapter.create().apply {
        url = chapterUrl
        name = chapterTitle?.takeIf(String::isNotBlank)
            ?.let { "Ch. $number - $it" }
            ?: "Chapter $number"
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(chapterDatePublished)
    }
}

@Serializable
class ChapterPageResponseDto(@SerialName("chapter_detail") private val chapterDetail: ChapterPageDto) {
    fun toPageList() = chapterDetail.toPageList()
}

@Serializable
class ChapterPageDto(
    private val server: String,
    @SerialName("chapter_content") private val chapterContent: String,
) {
    fun toPageList() = Jsoup.parseBodyFragment(chapterContent, server)
        .select("img[data-src]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("data-src")) }
}

private fun String.toTitleCase() = split(' ').joinToString(" ") { word ->
    word.lowercase().replaceFirstChar(Char::titlecase)
}
