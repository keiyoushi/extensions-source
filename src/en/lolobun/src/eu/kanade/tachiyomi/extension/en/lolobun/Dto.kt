package eu.kanade.tachiyomi.extension.en.lolobun

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ResponseDto<T>(
    private val status: StatusDto,
    private val data: T?,
) {
    fun requireData(): T = data ?: throw Exception(status.msg ?: "Request failed with code ${status.errorCode}")
}

@Serializable
class StatusDto(
    val errorCode: Int,
    val msg: String?,
)

@Serializable
class SearchDto(
    @SerialName("HasMore") val hasMore: Boolean,
    @SerialName("Items") val items: List<SearchItemDto>,
)

@Serializable
class SearchItemDto(
    @SerialName("EntityId") private val id: Int,
    @SerialName("Title") private val title: String,
    @SerialName("Cover") private val cover: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/c/$id"
        title = this@SearchItemDto.title
        thumbnail_url = cover?.let { if (it.startsWith("http")) it else "$COVER_URL/$it" }
    }
}

private const val COVER_URL = "https://osrs.sfacg.com/web/comic/images/Logo"
