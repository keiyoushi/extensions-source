package eu.kanade.tachiyomi.extension.ja.corocoroonline

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Proto
// Latest / Entries
@Serializable
class TitleListView(
    @ProtoNumber(1) val list: TitleList?,
)

@Serializable
class TitleList(
    @ProtoNumber(2) val titles: List<CsrTitle>,
)

@Serializable
class CsrTitle(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(3) private val name: String,
    @ProtoNumber(5) private val thumbnail: CsrImage?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/title/$id"
        title = name
        thumbnail_url = thumbnail?.url
    }
}

// Details
@Serializable
class TitleDetailView(
    @ProtoNumber(2) private val title: CsrTitle,
    @ProtoNumber(3) private val authors: List<CsrAuthor>?,
    @ProtoNumber(6) private val description: String?,
    @ProtoNumber(8) val chapters: List<CsrChapter>,
) {
    fun toSManga() = title.toSManga().apply {
        author = authors?.joinToString { it.name }
        description = this@TitleDetailView.description
    }
}

@Serializable
class CsrChapter(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(5) private val points: CsrPoints?,
    @ProtoNumber(9) private val startEpoch: Long,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/chapter/$id"
        val prefix = if (points?.point != null) "🔒 " else ""
        name = "$prefix${this@CsrChapter.name}"
        date_upload = startEpoch * 1000L

        val match = numberRegex.find(name)
        if (match != null) {
            val num = match.groupValues[1]
            val decimal = match.groupValues[2]
            val numberStr = if (decimal.isNotEmpty()) "$num.$decimal" else num
            chapter_number = numberStr.toFloat()
        }
    }
    companion object {
        private val numberRegex = Regex("""第(\d+)(?:\.(\d+))?話""")
    }
}

@Serializable
class CsrAuthor(
    @ProtoNumber(2) val name: String,
)

@Serializable
class CsrPoints(
    @ProtoNumber(2) val point: Int?,
)

// Viewer
@Serializable
class ViewerView(
    @ProtoNumber(2) val pages: List<ViewerImage>,
    @ProtoNumber(19) val aesKey: String,
    @ProtoNumber(20) val aesIv: String,
)

@Serializable
class ViewerRequest

@Serializable
class CsrImage(
    @ProtoNumber(1) val url: String,
)

@Serializable
class ViewerImage(
    @ProtoNumber(1) val url: String,
)

// Json
@Serializable
class RscRankingContainer(
    val rankingList: List<RscRankingCategory>,
)

@Serializable
class RscRankingCategory(
    val rankingTypeName: String,
    val titles: List<RscRankingTitle>,
)

@Serializable
class RscRankingTitle(
    private val id: Int,
    private val name: String,
    private val thumbnail: RscRankingThumbnail,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$id"
        title = name
        thumbnail_url = thumbnail.src
    }
}

@Serializable
class RscRankingThumbnail(
    val src: String,
)
