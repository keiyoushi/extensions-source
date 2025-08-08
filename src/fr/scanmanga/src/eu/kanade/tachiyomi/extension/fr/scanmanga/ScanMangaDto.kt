package eu.kanade.tachiyomi.extension.fr.scanmanga

import kotlinx.serialization.Serializable

@Serializable
data class Page(
    val f: String, // filename
    val e: String, // extension
)

@Serializable
data class UrlPayload(
    val dN: String,
    val s: String,
    val v: String,
    val c: String,
    val p: Map<String, Page>,
) {
    fun generateImageUrls(): Map<Int, String> {
        val baseUrl = "https://$dN/$s/$v/$c"
        return p.entries
            .mapNotNull { (key, page) ->
                key.toIntOrNull()?.let { pageIndex ->
                    pageIndex to "$baseUrl/${page.f}.${page.e}"
                }
            }
            .sortedBy { it.first } // sort by page index
            .toMap()
    }
}

@Serializable
data class MangaSearchDto(
    val title: List<MangaItemDto>?,
)

@Serializable
data class MangaItemDto(
    val nom_match: String?,
    val url: String?,
    val image: String?,
)
