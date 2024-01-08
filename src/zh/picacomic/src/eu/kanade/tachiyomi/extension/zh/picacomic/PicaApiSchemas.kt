package eu.kanade.tachiyomi.extension.zh.picacomic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PicaLoginPayload(
    val email: String,
    val password: String,
)

@Serializable
data class PicaSearchPayload(
    val keyword: String,
    val categories: List<String>,
    val sort: String,
)

@Serializable
data class PicaResponse(
    val data: PicaData,
)

@Serializable
data class PicaData(
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
data class PicaSearchComics(
    val page: Int,
    val pages: Int,
    val docs: List<PicaSearchComic>,
)

// /comics/advanced-search, /comics/random, /comics/leaderboard
@Serializable
data class PicaSearchComic(
    val title: String,
    val _id: String,
    val thumb: PicaImage,
    val finished: Boolean,
    val categories: List<String>,
    val update_at: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val chineseTeam: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class PicaChapters(
    val docs: List<PicaChapter>,
    val page: Int,
    val pages: Int,
)

@Serializable
data class PicaChapter(
    val _id: String,
    val order: Int,
    val title: String,
    val updated_at: String,
)

@Serializable
data class PicaPages(
    val docs: List<PicaPage>,
    val page: Int,
    val pages: Int,
    val limit: Int,
)

@Serializable
data class PicaPage(
    val media: PicaImage,
)

@Serializable
data class PicaImage(
    val path: String,
    val fileServer: String,
)
