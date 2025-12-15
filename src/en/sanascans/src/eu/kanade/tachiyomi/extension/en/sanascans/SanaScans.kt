package eu.kanade.tachiyomi.extension.en.sanascans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Response
import java.text.Normalizer
import java.util.Locale

class SanaScans : Iken(
    "Sana Scans",
    "en",
    "https://sanascans.com",
    "https://api.sanascans.com",
) {

    override fun searchMangaParse(response: Response): MangasPage {
        val result = super.searchMangaParse(response)
        val normalizedQuery = response.request.url.queryParameter("searchTerm").normalizeForSearch()

        if (normalizedQuery.isEmpty()) {
            return result
        }

        val queryTokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }

        val filtered = result.mangas.filter { manga ->
            val searchableFields = listOf(
                manga.title.normalizeForSearch(),
                manga.description.normalizeForSearch(),
            )

            queryTokens.all { token ->
                searchableFields.any { field -> field.contains(token) }
            }
        }

        return MangasPage(filtered, result.hasNextPage)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.selectFirst("svg.lucide-lock") != null) {
            throw Exception("Unlock chapter in webview")
        }

        val pages = document.getNextJson("images")
            .parseAs<List<SanaPageDto>>()
            .let { pageDtos ->
                // Sana sometimes assigns sequential `order` values to the wrong image URLs,
                // pushing some pages (eg "04 …") to the end. The filename itself usually
                // starts with the actual page number, so prefer that when present.
                val hasPageNumbers = pageDtos.count { it.url.extractLeadingPageNumber() != null }
                val useFilenameOrdering = hasPageNumbers >= (pageDtos.size / 2)

                if (useFilenameOrdering) {
                    pageDtos.sortedWith(
                        compareBy<SanaPageDto> { it.url.extractLeadingPageNumber() ?: Int.MAX_VALUE }
                            .thenBy { it.order ?: Int.MAX_VALUE },
                    )
                } else {
                    pageDtos.sortedBy { it.order ?: Int.MAX_VALUE }
                }
            }

        return pages.mapIndexed { idx, p ->
            Page(idx, imageUrl = p.url.encodeSpaces())
        }
    }

    private fun String.encodeSpaces(): String = replace(" ", "%20")

    private fun String.extractLeadingPageNumber(): Int? {
        val fileName = substringAfterLast('/')
        val match = leadingPageNumberRegex.find(fileName) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun String?.normalizeForSearch(): String {
        if (this.isNullOrBlank()) return ""

        val base = Normalizer.normalize(this, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT)
            .replace(diacriticsRegex, "")

        val collapsed = nonAlphanumericRegex.replace(base, " ").trim()

        return multiSpaceRegex.replace(collapsed, " ").trim()
    }

    companion object {
        private val diacriticsRegex = Regex("\\p{M}+")
        private val nonAlphanumericRegex = Regex("[^a-z0-9]+")
        private val multiSpaceRegex = Regex("\\s+")
        private val leadingPageNumberRegex = Regex("^0*(\\d{1,5})")
    }

    @Serializable
    private data class SanaPageDto(
        val url: String,
        val order: Int? = null,
    )
}
