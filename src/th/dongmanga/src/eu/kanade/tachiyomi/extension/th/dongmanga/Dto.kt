package eu.kanade.tachiyomi.extension.th.dongmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Series(
    @SerialName("f") private val fid: Int,
    @SerialName("n") val name: String,
    @SerialName("s") private val altNames: String? = null,
    @SerialName("g") val category: String? = null,
    @SerialName("c") private val cover: String? = null,
    @SerialName("d") private val summary: String? = null,
    @SerialName("u") val lastThreadId: Long = 0,
    @SerialName("v") val views: Long = 0,
    @SerialName("r") val rating: Double = 0.0,
    @SerialName("rc") val votes: Int = 0,
    @SerialName("e") private val ended: Int = 0,
) {
    val isEnded get() = ended != 0

    val searchText get() = "$name ${altNames.orEmpty()} ${category.orEmpty()} ${summary.orEmpty()}".lowercase()

    fun toSManga() = SManga.create().apply {
        url = fid.toString()
        title = name
        thumbnail_url = cover
    }
}

@Serializable
class PagesDto(val img: List<String>)
