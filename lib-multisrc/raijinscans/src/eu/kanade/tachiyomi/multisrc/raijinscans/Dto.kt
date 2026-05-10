package eu.kanade.tachiyomi.multisrc.raijinscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody

@Serializable
class LatestUpdatesDto(
    val success: Boolean,
    val data: LatestUpdatesDataDto,
)

@Serializable
class ReaderManifest(
    val ajaxUrl: String,
    val nonce: String,
    val token: String,
    val mangaId: Int,
    val chapterId: Int,
    val chapterSlug: String,
    val host: String,
    val offset: Int,
    val limit: Int,
)

fun ReaderManifest.toFormBody(): FormBody {
    val formBuilder = FormBody.Builder()
    formBuilder.add("action", "raijin_free_reader_manifest")
    formBuilder.add("nonce", nonce)
    formBuilder.add("token", token)
    formBuilder.add("manga_id", mangaId.toString())
    formBuilder.add("chapter_id", chapterId.toString())
    formBuilder.add("chapter_slug", chapterSlug)
    formBuilder.add("host", host)
    formBuilder.add("offset", offset.toString())
    formBuilder.add("limit", limit.toString())
    return formBuilder.build()
}

@Serializable
class ManifestResponse(
    val data: ManifestResponseData,
)

@Serializable
class ManifestResponseData(
    val images: List<Image>,
)

@Serializable
class Image(
    val number: String,
    val url: String,
)

@Serializable
class LatestUpdatesDataDto(
    @SerialName("manga_html") val mangaHtml: String,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)
