package eu.kanade.tachiyomi.extension.all.mangamillion

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class SeriesResponse(
    @ProtoNumber(22) val allSeries: AllSeries,
)

@Serializable
class SearchResponse(
    @ProtoNumber(20) val allSeries: AllSeries,
)

@Serializable
class AllSeries(
    @ProtoNumber(1) val seriesList: List<SeriesList>,
)

@Serializable
class SeriesList(
    @ProtoNumber(1) val series: Series,
    @ProtoNumber(2) val languages: List<String>,
)

@Serializable
class Series(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val cover: String?,
    @ProtoNumber(3) private val name: String,
    @ProtoNumber(7) val views: Long?,
    @ProtoNumber(9) val uploadTime: Long?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = cover
    }
}

@Serializable
class DetailsResponse(
    @ProtoNumber(50) val detailsEntry: DetailsEntry,
)

@Serializable
class DetailsEntry(
    @ProtoNumber(1) val details: Details,
)

@Serializable
class Details(
    @ProtoNumber(1) private val cover: String?,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(3) private val authorName: String?,
    @ProtoNumber(4) private val genres: List<Tag>?,
    @ProtoNumber(7) private val summary: String?,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        description = summary
        genre = genres?.joinToString { it.name }
        author = authorName
        thumbnail_url = cover
    }
}

@Serializable
class Tag(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val name: String,
)

@Serializable
class SearchParameterResponse(
    @ProtoNumber(21) val searchParameter: SearchParameter,
)

@Serializable
class SearchParameter(
    @ProtoNumber(2) val genres: List<Tag> = emptyList(),
    @ProtoNumber(3) val themes: List<Tag> = emptyList(),
    @ProtoNumber(4) val highlights: List<Tag> = emptyList(),
    @ProtoNumber(5) val ratings: List<Tag> = emptyList(),
)

@Serializable
class ChapterResponse(
    @ProtoNumber(60) val chapterEntry: ChapterEntry,
)

@Serializable
class ChapterEntry(
    @ProtoNumber(2) val chapterGroups: List<ChapterGroup>,
)

@Serializable
class ChapterGroup(
    @ProtoNumber(2) val chapterList: List<ChapterList> = emptyList(),
)

@Serializable
class ChapterList(
    @ProtoNumber(1) private val chapterNumber: String,
    @ProtoNumber(2) private val chapterName: String,
    @ProtoNumber(3) private val id: Int?,
) {
    val isAvailable get() = id != null

    fun toSChapter(titleId: String) = SChapter.create().apply {
        url = id.toString()
        chapter_number = chapterNumbers
        name = chapterName
        memo = buildJsonObject {
            put("titleId", titleId)
        }
    }

    private val chapterNumbers: Float get() {
        if (!chapterNumber.startsWith("#")) {
            return -1F
        }

        val numbers = chapterNumber.removePrefix("#")

        return if (numbers.contains(",")) {
            numbers.split(",")[0].toFloat()
        } else if (numbers.contains("-")) {
            val parts = chapterNumber.removePrefix("#").split("-", limit = 2)

            if (parts.size == 1 || parts[1].length >= 3) {
                parts[0].toFloat()
            } else {
                "${parts[0]}.${parts[1]}".toFloat()
            }
        } else {
            numbers.toFloat()
        }
    }
}

@Serializable
class ViewerResponse(
    @ProtoNumber(70) val viewer: Viewer,
)

@Serializable
class Viewer(
    @ProtoNumber(1) val pageList: List<PageList>,
    @ProtoNumber(7) val key: String,
    @ProtoNumber(8) val iv: String,
)

@Serializable
class PageList(
    @ProtoNumber(1) val imageUrl: String,
)

@Serializable
class TokenResponse(
    @ProtoNumber(170) val token: Token,
)

@Serializable
class Token(
    @ProtoNumber(1) val accessToken: String,
)
