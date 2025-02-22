package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BilibiliResultDto<T>(
    val code: Int = 0,
    val data: T? = null,
    @SerialName("msg") val message: String = "",
)

@Serializable
data class BilibiliSearchDto(
    val list: List<BilibiliComicDto> = emptyList(),
)

@Serializable
data class BilibiliComicDto(
    @SerialName("author_name") val authorName: List<String> = emptyList(),
    @SerialName("classic_lines") val classicLines: String = "",
    @SerialName("comic_id") val comicId: Int = 0,
    @SerialName("ep_list") val episodeList: List<BilibiliEpisodeDto> = emptyList(),
    val id: Int = 0,
    @SerialName("is_finish") val isFinish: Int = 0,
    @SerialName("temp_stop_update") val isOnHiatus: Boolean = false,
    @SerialName("season_id") val seasonId: Int = 0,
    val styles: List<String> = emptyList(),
    val title: String,
    @SerialName("update_weekday") val updateWeekdays: List<Int> = emptyList(),
    @SerialName("vertical_cover") val verticalCover: String = "",
) {
    val hasPaidChapters: Boolean
        get() = paidChaptersCount > 0

    val paidChaptersCount: Int
        get() = episodeList.filter { it.isPaid }.size
}

@Serializable
data class BilibiliEpisodeDto(
    val id: Int,
    @SerialName("is_in_free") val isInFree: Boolean,
    @SerialName("is_locked") val isLocked: Boolean,
    @SerialName("pay_gold") val payGold: Int,
    @SerialName("pay_mode") val payMode: Int,
    @SerialName("pub_time") val publicationTime: String,
    @SerialName("short_title") val shortTitle: String,
    val title: String,
) {
    val isPaid = payMode == 1 && payGold > 0
}

@Serializable
data class BilibiliReader(
    val images: List<BilibiliImageDto> = emptyList(),
)

@Serializable
data class BilibiliImageDto(
    val path: String,
    @SerialName("x") val width: Int,
    @SerialName("y") val height: Int,
) {

    fun url(quality: String, format: String): String {
        val imageWidth = if (quality == "raw") "${width}w" else quality

        return "$path@$imageWidth.$format"
    }
}

@Serializable
data class BilibiliPageDto(
    val token: String,
    val url: String,
    @SerialName("complete_url")
    val completeUrl: String,
) {
    val imageUrl: String
        get() = completeUrl.ifEmpty { "$url?token=$token" }
}

@Serializable
data class BilibiliAccessTokenCookie(
    val accessToken: String,
    val refreshToken: String,
    val area: String,
)

@Serializable
data class BilibiliAccessToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class BilibiliUserEpisodes(
    @SerialName("unlocked_eps") val unlockedEpisodes: List<BilibiliUnlockedEpisode>? = emptyList(),
)

@Serializable
data class BilibiliUnlockedEpisode(
    @SerialName("ep_id") val id: Int = 0,
)

@Serializable
data class BilibiliGetCredential(
    @SerialName("comic_id") val comicId: Int,
    @SerialName("ep_id") val episodeId: Int,
    val type: Int,
)

@Serializable
data class BilibiliCredential(
    val credential: String,
)
