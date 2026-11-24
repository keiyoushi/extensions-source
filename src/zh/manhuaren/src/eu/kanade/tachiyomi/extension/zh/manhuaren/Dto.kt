package eu.kanade.tachiyomi.extension.zh.manhuaren

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.format.DateTimeFormatter

private const val NOPIC_IMAGE = "http://mhfm5.tel.cdndm5.com/tag/category/nopic.jpg"
private val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@Serializable
class ManhuarenResponse<T>(val response: T)

@Serializable
class ErrorResponse(val errorResponse: JsonElement? = null)

@Serializable
class TokenResponse(val tokenResult: TokenResult, val userId: Long)

@Serializable
class TokenResult(val scheme: String, val parameter: String)

@Serializable
class MangaListDto(
    private val mangas: List<MangaDto>? = null,
    private val result: List<MangaDto>? = null,
) {
    fun toMangasPage(): MangasPage {
        val list = (result ?: mangas).orEmpty().map { it.toSManga() }
        return MangasPage(list, list.isNotEmpty())
    }
}

@Serializable
class MangaDto(
    private val mangaId: Int,
    private val mangaName: String,
    private val mangaCoverimageUrl: String,
    private val mangaIsOver: Int,
    private val mangaAuthor: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = mangaName
        thumbnail_url = mangaCoverimageUrl
        author = mangaAuthor.orEmpty()
        status = when (mangaIsOver) {
            1 -> SManga.COMPLETED
            0 -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        url = "/v1/manga/getDetail?mangaId=$mangaId"
    }
}

@Serializable
class MangaDetailDto(
    private val mangaName: String,
    private val mangaTheme: String,
    private val mangaIsOver: Int,
    private val mangaIntro: String,
    private val mangaAuthors: List<String>,
    private val mangaCoverimageUrl: String? = null,
    private val mangaPicimageUrl: String? = null,
    private val shareIcon: String? = null,
    private val mangaEpisode: List<ChapterDto> = emptyList(),
    private val mangaWords: List<ChapterDto> = emptyList(),
    private val mangaRolls: List<ChapterDto> = emptyList(),
) {
    fun toSManga(manga: SManga) {
        val cover = mangaCoverimageUrl.orEmpty()
        var thumbnail = cover
        if (cover.isEmpty() || cover == NOPIC_IMAGE) {
            thumbnail = mangaPicimageUrl?.takeIf { it.isNotEmpty() } ?: cover
        }
        if (thumbnail.isEmpty()) {
            thumbnail = shareIcon?.takeIf { it.isNotEmpty() } ?: thumbnail
        }

        manga.title = mangaName
        manga.thumbnail_url = thumbnail
        manga.author = mangaAuthors.joinToString()
        manga.genre = mangaTheme.replace(" ", ", ")
        manga.status = when (mangaIsOver) {
            1 -> SManga.COMPLETED
            0 -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        manga.description = mangaIntro
    }

    fun toChapterList(): List<SChapter> = buildList {
        addAll(mangaEpisode.map { it.toSChapter("mangaEpisode") })
        addAll(mangaWords.map { it.toSChapter("mangaWords") })
        addAll(mangaRolls.map { it.toSChapter("mangaRolls") })
    }
}

@Serializable
class ChapterDto(
    private val sectionName: String,
    private val sectionTitle: String,
    private val sectionSort: Int,
    private val sectionId: Int,
    private val isMustPay: Int,
    private val releaseTime: String,
) {
    fun toSChapter(type: String): SChapter = SChapter.create().apply {
        name = (if (isMustPay == 1) "(锁) " else "") +
            (if (type == "mangaEpisode") "[番外] " else "") + sectionName +
            (if (sectionTitle.isEmpty()) "" else ": $sectionTitle")
        date_upload = chapterDateFormat.tryParseDate(releaseTime)
        chapter_number = sectionSort.toFloat()
        url = "/v1/manga/getRead?mangaSectionId=$sectionId"
    }
}

@Serializable
class PageListDto(
    private val hostList: List<String>,
    private val mangaSectionImages: List<String>,
    private val query: String,
) {
    fun toPageList(): List<Page> {
        val host = hostList.first()
        return mangaSectionImages.mapIndexed { index, image -> Page(index, imageUrl = "$host$image$query") }
    }
}

@Serializable
class AnonyUserRequest(private val keys: List<AnonyUserKey>)

@Serializable
class AnonyUserKey(private val key: String, private val keyType: String)
