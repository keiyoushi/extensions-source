package eu.kanade.tachiyomi.extension.en.mangamirai
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ViewerResponse(
    val records: List<Record>,
)

@Serializable
class Record(
    val page: Int,
    @SerialName("scramble_key") val scrambleKey: String,
    val url: String,
)
