package eu.kanade.tachiyomi.extension.ar.mangatime

import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Response

inline fun <reified T> Response.parseTrpcList(): T = this.parseAs<List<TrpcResponse<T>>>().first().result.data.json

@Serializable
class TrpcResponse<T>(
    val result: TrpcResult<T>,
)

@Serializable
class TrpcResult<T>(
    val data: TrpcData<T>,
)

@Serializable
class TrpcData<T>(
    val json: T,
)

// List Manga

@Serializable
class MangaListData(
    val results: List<MangaSeries>,
    val hasMore: Boolean,
)

@Serializable
class MangaSeries(
    val id: String,
    val title: String,
    val slug: String,
    val coverUrl: String,
    val type: String,
)

// Details

@Serializable
class SeriesDto(
    val title: String,
    val slug: String,
    val coverUrl: String,
    val type: String,
    val genres: List<Genre>?,
    val description: String?,
    val status: String?,
)

@Serializable
class Genre(
    val name: String,
)

// Chapters

@Serializable
class ChaptersDto(
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    val number: Int,
    val title: String,
    val publishedAt: String?,
)

// pages
@Serializable
class TrpcPages(
    val result: TrpcResultPages,
)

@Serializable
class TrpcResultPages(
    val data: TrpcDataPages,
)

@Serializable
class TrpcDataPages(
    val json: PagesDto,
)

@Serializable
class PagesDto(
    val pages: List<String>,
    val isUnlocked: Boolean,
    val id: String,
    val seriesId: String,
)

// Trpc Request

inline fun <reified T> T.trpcJson(): String = TrpcRequest(TrpcData(this)).toJsonString()

@Serializable
class TrpcRequest<T>(
    @SerialName("0")
    val first: TrpcData<T>,
)

@Serializable
class SearchDto(
    val page: Int,
    val limit: Int,
    val sortBy: String,
    val sortOrder: String,
    val query: String? = null,
)

@Serializable
class SeriesSlug(
    val slug: String,
)

@Serializable
class ChaptersQuery(
    val seriesId: String,
    val limit: Int,
)

@Serializable
class PagesQuery(
    val seriesSlug: String,
    val chapterNumber: Int,
)

@Serializable
class ViewQuery(
    val seriesId: String,
    val chapterId: String,
)
