package eu.kanade.tachiyomi.extension.ja.mangamee

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.toString

@Serializable
class RankingResponse(
    val all: RankingData,
)

@Serializable
class RankingData(
    val rankingList: List<RankingList>,
)

@Serializable
class RankingList(
    val name: String?,
    val titles: List<MangaTitle>,
)

@Serializable
class MangaTitle(
    private val id: Int,
    private val name: String,
    private val largeImage: Thumbnail?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = largeImage?.src
    }
}

@Serializable
class Thumbnail(
    val src: String?,
)

@Serializable
class LatestResponse(
    val titleGroup: RankingList,
)

@Serializable
class SearchResponse(
    val popularTitles: RankingList,
)

@Serializable
class DetailResponse(
    @ProtoNumber(1) private val titles: Title,
    @ProtoNumber(3) private val tags: List<Tags>?,
    @ProtoNumber(13) val pages: List<Pages>?,
) {
    fun toSManga() = SManga.create().apply {
        title = titles.name
        author = titles.mangaka
        description = buildString {
            titles.descriptionText?.let { append(it) }
            if (titles.kanaName != null) {
                append("\n\nAlternative Title: ${titles.kanaName}")
            }
        }
        thumbnail_url = titles.thumbnail
        genre = tags?.joinToString { it.name }
    }
}

@Serializable
class Title(
    @ProtoNumber(2) val name: String,
    @ProtoNumber(4) val descriptionText: String?,
    @ProtoNumber(6) val mangaka: String?,
    @ProtoNumber(9) val thumbnail: String?,
    @ProtoNumber(17) val kanaName: String?,
)

@Serializable
class Tags(
    @ProtoNumber(2) val name: String,
)

@Serializable
class ChapterResponse(
    val allEpisodes: Chapters,
)

@Serializable
class Chapters(
    val episodes: List<ChapterList>,
)

@Serializable
class ChapterList(
    private val id: Int,
    private val title: String,
    private val isFree: Boolean?,
) {
    val isLocked: Boolean
        get() = isFree == false

    fun toSChapter(titleId: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = "$id#$titleId"
        name = lock + title
    }
}

@Serializable
class Pages(
    @ProtoNumber(1) val mainPage: ViewerImages?,
)

@Serializable
class ViewerImages(
    @ProtoNumber(2) val imageUrl: String,
    @ProtoNumber(7) val key: String,
)
