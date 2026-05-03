package eu.kanade.tachiyomi.extension.es.jeazscans

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiLectorResponse(
    val success: Boolean = false,
    val paginas: List<ApiLectorPage> = emptyList(),
)

@Serializable
class ApiLectorPage(
    val orden: Int,
    @SerialName("data_verify") val dataVerify: String,
)

@Serializable
class SearchResponseItem(
    private val id: Int,
    private val titulo: String,
    private val portada: String?,
) {
    fun toSManga(baseUrl: String): SManga? {
        if (id == -1 || titulo.isBlank()) return null
        return SManga.create().apply {
            url = "/manga.php?id=$id"
            title = titulo
            if (!portada.isNullOrBlank()) {
                thumbnail_url = if (portada.startsWith("http")) portada else "$baseUrl/${portada.trimStart('/')}"
            }
        }
    }
}
