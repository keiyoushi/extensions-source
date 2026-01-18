package eu.kanade.tachiyomi.extension.en.sanascans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.Normalizer
import java.util.Locale

class SanaScans : Iken(
    "Sana Scans",
    "en",
    "https://sanascans.com",
    "https://api.sanascans.com",
) {

    override val sortPagesByFilename = true

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
    }
}
