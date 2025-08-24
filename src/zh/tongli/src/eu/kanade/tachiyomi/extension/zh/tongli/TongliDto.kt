package eu.kanade.tachiyomi.extension.zh.tongli

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class PopularResponseDto(@SerialName("RankingSet") val rankingSet: List<RankingSetDto>)

@Serializable
class RankingSetDto(@SerialName("Week") val week: List<MangaDto>)

@Serializable
class MangaDto(
    @JsonNames("BookTitle", "Title") private val bookTitle: String,
    @JsonNames("BookCoverURL", "CoverURL") private val bookCoverURL: String,
    @SerialName("BookGroupID") private val bookGroupID: String,
    @SerialName("IsSerial") private val isSerial: Boolean,
) {
    fun toSManga() = SManga.create().apply {
        url = "$bookGroupID,$isSerial"
        title = bookTitle
        thumbnail_url = bookCoverURL
    }
}

@Serializable
class LatestResponseDto(
    @SerialName("TotalPage") val totalPage: Int,
    @SerialName("Page") val page: Int,
    @SerialName("Books") val books: List<MangaDto>,
)

@Serializable
class ChapterDto(
    @SerialName("BookID") private val bookID: String,
    @SerialName("Vol") private val vol: String,
    @SerialName("IsUpcoming") private val isUpcoming: Boolean,
    @SerialName("IsPurchased") private val isPurchased: Boolean,
    @SerialName("IsFree") private val isFree: Boolean,
) {
    fun toSChapter(): SChapter? = SChapter.create().apply {
        if (isUpcoming) return null
        url = bookID
        // Prepend lock emoji to name if not readable
        name = if (isFree || isPurchased) vol else "\uD83D\uDD12 $vol"
    }
}

@Serializable
class DetailsDto(
    @SerialName("Title") private val title: String,
    @SerialName("CoverURL") private val coverURL: String,
    @SerialName("Authors") private val authors: List<AuthorDto>,
    @SerialName("Introduction") private val introduction: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@DetailsDto.title
        thumbnail_url = coverURL
        author = authors.joinToString {
            if (it.title.isNullOrEmpty()) it.name else "${it.title}：${it.name}"
        }
        description = introduction
    }
}

@Serializable
class AuthorDto(@SerialName("Name") val name: String, @SerialName("Title") val title: String?)

@Serializable
class PageListResponseDto(@SerialName("Pages") val pages: List<ImageDto>)

@Serializable
class ImageDto(@SerialName("ImageURL") val imageURL: String)

@Serializable
class TokenResponseDto(
    @JsonNames("id_token") val idToken: String,
    @JsonNames("refresh_token") val refreshToken: String,
)
