package eu.kanade.tachiyomi.extension.ja.ganganonline

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat

@Serializable
class NextData<T>(
    val props: Props<T>,
)

@Serializable
class Props<T>(
    val pageProps: PageProps<T>,
)

@Serializable
class PageProps<T>(
    val data: T,
)

@Serializable
class MangaListDto(
    val titleSections: List<MangaSectionDto>?, // Popular/Finished
    val sections: List<SearchSectionDto>?, // Search
    val ongoingTitleSection: MangaSectionDto?, // GA
    val finishedTitleSection: MangaSectionDto?, // GA
)

@Serializable
class MangaSectionDto(
    val titles: List<MangaDto>,
)

@Serializable
class SearchSectionDto(
    val titleLinks: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val titleId: Int,
    @JsonNames("header", "name")
    private val title: String,
    private val imageUrl: String?,
    val isNovel: Boolean?,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = "/title/$titleId"
        title = this@MangaDto.title
        thumbnail_url = baseUrl + imageUrl
    }
}

@Serializable
class PixivPageDto(
    val ganganTitles: List<MangaDto>?,
)

@Serializable
class MangaDetailDto(
    val default: MangaDetailDefaultDto,
)

@Serializable
class MangaDetailDefaultDto(
    private val titleName: String,
    private val author: String,
    private val description: String,
    private val imageUrl: String,
    val chapters: List<ChapterDto>,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = titleName
        author = this@MangaDetailDefaultDto.author
        description = this@MangaDetailDefaultDto.description
        thumbnail_url = baseUrl + imageUrl
    }
}

@Serializable
class ChapterDto(
    private val id: Int?,
    val status: Int?,
    private val mainText: String,
    private val subText: String?,
    private val publishingPeriod: String?,
) {
    fun toSChapter(mangaUrl: String, dateFormat: SimpleDateFormat): SChapter = SChapter.create().apply {
        url = "$mangaUrl/chapter/$id"
        name = mainText + if (!subText.isNullOrEmpty()) " - $subText" else ""
        date_upload = publishingPeriod?.substringBefore("〜").let { dateFormat.tryParse(it) }
    }
}

@Serializable
class PageListDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val image: PageImageUrlDto?,
    val linkImage: PageImageUrlDto?,
)

@Serializable
class PageImageUrlDto(
    val imageUrl: String,
)
