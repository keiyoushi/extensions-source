package eu.kanade.tachiyomi.extension.fr.scanmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Page(
    val f: String, // filename
    val e: String, // extension
)

@Serializable
class UrlPayload(
    private val dN: String,
    private val s: String,
    private val v: String,
    private val c: String,
    private val p: Map<String, Page>,
) {
    fun generateImageUrls(): List<Pair<Int, String>> {
        val baseUrl = "https://$dN/$s/$v/$c"
        return p.entries
            .mapNotNull { (key, page) ->
                key.toIntOrNull()?.let { pageIndex ->
                    pageIndex to "$baseUrl/${page.f}.${page.e}"
                }
            }
            .sortedBy { it.first } // sort by page index
    }
}

@Serializable
class LelRequestDto(
    private val a: String, // sme
    private val b: String, // sml
    private val c: String, // fingerprint
)

@Serializable
class MangaSearchDto(
    val title: List<MangaItemDto>?,
)

@Serializable
class MangaItemDto(
    @SerialName("nom_match") val nomMatch: String,
    val url: String,
    val image: String,
)
