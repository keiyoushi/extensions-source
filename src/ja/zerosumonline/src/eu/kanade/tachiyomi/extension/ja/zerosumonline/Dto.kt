package eu.kanade.tachiyomi.extension.ja.zerosumonline

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class TitleListView(
    @ProtoNumber(3) val titles: List<ApiTitle>,
)

@Serializable
class TitleDetailView(
    @ProtoNumber(2) val title: ApiTitle,
    @ProtoNumber(3) val chapters: List<ApiChapter>,
)

@Serializable
class ApiTitle(
    @ProtoNumber(2) val slug: String,
    @ProtoNumber(3) private val name: String,
    @ProtoNumber(4) private val altTitle: String?,
    @ProtoNumber(5) private val authors: String?,
    @ProtoNumber(7) private val description: String?,
    @ProtoNumber(8) private val thumbnail: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/detail/$slug"
        title = name
        author = authors
        description = buildString {
            this@ApiTitle.description?.let { append(it) }
            altTitle?.let {
                if (isNotEmpty()) append("\n\n\n\n")
                append(it)
            }
        }
        thumbnail_url = thumbnail
    }
}

@Serializable
class ApiChapter(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(4) private val publishedAt: Long,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = "/detail/$slug/$id"
        name = this@ApiChapter.name
        date_upload = publishedAt * 1000L
    }
}

@Serializable
class ViewerView(
    @ProtoNumber(5) val pages: List<ViewerImage>,
)

@Serializable
class ViewerImage(
    @ProtoNumber(1) val url: String = "",
)

@Serializable
class ViewerRequest(
    @Suppress("unused")
    @ProtoNumber(1)
    private val chapterId: Int,
)
