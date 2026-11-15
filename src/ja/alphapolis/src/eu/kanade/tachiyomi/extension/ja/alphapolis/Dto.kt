package eu.kanade.tachiyomi.extension.ja.alphapolis

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class ChapterResponse(
    val episodes: List<Episode>,
)

@Serializable
class Episode(
    private val url: String,
    private val mainTitle: String,
    private val upTime: String?,
    private val rental: Rental?,
) {
    val isLocked: Boolean
        get() = rental?.isFree == false && rental.isOnRental != true

    fun toSChapter(baseUrl: String) = SChapter.create().apply {
        val mangaUrl = (baseUrl + this@Episode.url).toHttpUrl()
        val lock = if (isLocked) "🔒 " else ""
        url = mangaUrl.pathSegments[3]
        name = lock + mainTitle
        date_upload = dateFormat.tryParseDate(upTime)
        memo = buildJsonObject {
            put("mangaId", mangaUrl.pathSegments[2].toInt())
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd'更新'", Locale.ROOT)

@Serializable
class Rental(
    val isFree: Boolean?,
    val isOnRental: Boolean?,
)

@Suppress("unused")
@Serializable
class ChapterRequestBody(
    @SerialName("manga_id") private val mangaId: Int,
)

@Serializable
class ViewerResponse(
    val page: ViewerPage?,
)

@Serializable
class ViewerPage(
    val placeholder: String,
    val images: List<ViewerImage>,
)

@Serializable
class ViewerImage(
    val url: String,
)

@Suppress("unused")
@Serializable
class ViewerRequestBody(
    @SerialName("episode_no") private val episodeNo: Int,
    @SerialName("hide_page") private val hidePage: Boolean,
    @SerialName("manga_sele_id") private val mangaSeleId: Int,
    private val preview: Boolean,
    private val resolution: String,
)
