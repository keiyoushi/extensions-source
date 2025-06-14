package eu.kanade.tachiyomi.extension.all.webtoonstranslate

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class Result<T>(
    val result: T?,
    val code: String,
    val message: String? = null,
)

@Serializable
class TitleList(
    val totalCount: Int,
    val titleList: List<Title>,
)

@Serializable
class Title(
    private val titleNo: Int,
    private val teamVersion: Int,
    private val representTitle: String,
    private val writeAuthorName: String?,
    private val pictureAuthorName: String?,
    private val thumbnailIPadUrl: String?,
    private val thumbnailMobileUrl: String?,
) {
    private val thumbnailUrl: String?
        get() = (thumbnailIPadUrl ?: thumbnailMobileUrl)
            ?.let { "https://mwebtoon-phinf.pstatic.net$it" }

    fun toSManga(baseUrl: String, translateLangCode: String) = SManga.create().apply {
        url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("translate/episodeList")
            .addQueryParameter("titleNo", titleNo.toString())
            .addQueryParameter("languageCode", translateLangCode)
            .addQueryParameter("teamVersion", teamVersion.toString())
            .build()
            .toString()
        title = representTitle
        author = writeAuthorName
        artist = pictureAuthorName ?: writeAuthorName
        thumbnail_url = thumbnailUrl
    }
}

@Serializable
class EpisodeList(
    val episodes: List<Episode>,
)

@Serializable
class Episode(
    val translateCompleted: Boolean,
    private val titleNo: Int,
    private val episodeNo: Int,
    private val languageCode: String,
    private val teamVersion: Int,
    private val title: String,
    private val episodeSeq: Int,
    private val updateYmdt: Long,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/lineWebtoon/ctrans/translatedEpisodeDetail_jsonp.json?titleNo=$titleNo&episodeNo=$episodeNo&languageCode=$languageCode&teamVersion=$teamVersion"
        name = "$title #$episodeSeq"
        chapter_number = episodeSeq.toFloat()
        date_upload = updateYmdt
        scanlator = teamVersion.takeIf { it != 0 }?.toString() ?: "(wiki)"
    }
}

@Serializable
class ImageList(
    val imageInfo: List<Image>,
)

@Serializable
class Image(
    val imageUrl: String,
)
