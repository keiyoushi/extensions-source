package eu.kanade.tachiyomi.extension.zh.picacomic

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class PicaLoginPayload(
    val email: String,
    val password: String,
)

@Serializable
class PicaSearchPayload(
    val keyword: String,
    val sort: String,
)

@Serializable
class PicaResponse(
    val data: PicaData,
)

@Serializable
class PicaData(
    // /comics/advanced-search: PicaSearchComics
    // /comics/random: List<PicaSearchComic>
    val comics: JsonElement? = null,
    // /comics/comicId/eps?page=
    val eps: PicaChapters? = null,
    // /comics/comicId/order/chapterOrder/pages?page=
    val pages: PicaPages? = null,
    // /auth/sign-in
    val token: String? = null,
    // /comics/comicId
    val comic: PicaSearchComic? = null,
)

// /comics
@Serializable
class PicaSearchComics(
    val page: Int,
    val pages: Int,
    val docs: List<PicaSearchComic>,
)

// /comics/advanced-search, /comics/random, /comics/leaderboard
@Serializable
class PicaSearchComic(
    val title: String,
    @SerialName("_id") val id: String,
    val thumb: PicaImage,
    val finished: Boolean,
    val categories: List<String>,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
) {
    val genres get() = (tags ?: categories).map(String::trim)

    fun toSManga(apiUrl: String) = SManga.create().apply {
        title = this@PicaSearchComic.title
        author = this@PicaSearchComic.author
        artist = this@PicaSearchComic.artist
        description = this@PicaSearchComic.description
        genre = genres.distinct().takeIf { it.isNotEmpty() }?.joinToString()
        thumbnail_url = thumb.url
        url = "$apiUrl/comics/$id"
        status = if (finished) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class PicaChapters(
    val docs: List<PicaChapter>,
    val page: Int,
    val pages: Int,
)

@Serializable
class PicaChapter(
    val order: Int,
    val title: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
class PicaPages(
    val docs: List<PicaPage>,
    val page: Int,
    val pages: Int,
    val limit: Int,
)

@Serializable
class PicaPage(
    val media: PicaImage,
)

@Serializable
class PicaImage(
    val path: String,
    val fileServer: String,
) {
    val url get() = "$fileServer/static/$path"
}

@Serializable
class PicaChannel(
    val status: String,
    val addresses: List<String>,
)
