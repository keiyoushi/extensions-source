package eu.kanade.tachiyomi.extension.en.ritharscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class RitharScans : Keyoapp() {

    override suspend fun requestGeneres() = client.get("$baseUrl/search")

    override fun parseGenres(document: Document) = document.select("[x-data*=genre] button").associate {
        it.text() to it.attr("wire:key")
    }

    override fun searchUrlBuilder(query: String, page: Int) = "$baseUrl/search".toHttpUrl().newBuilder().apply {
        if (query.isNotBlank()) {
            addQueryParameter("title", query)
        }
    }

    // Server-side
    override fun Element.matchesGenres(genres: List<String>) = true
    override fun Element.matchesStatuses(statuses: List<String>) = true

    override fun searchMangaSelector() = "[wire:snapshot*=pages.search] button[tags]"

    override val altNameSelector: String = "div.font-medium:containsOwn(Alternative titles) ~ div span.select-all"
    override val statusSelector = "[alt=Status]"
    override val typeSelector = "[alt=Type]"

    override val paidChapterSelector = "img[alt~=Coin], img[src*=star-circle]"

    override fun pageListParse(document: Document): List<Page> {
        val xData = document.selectFirst("[x-data*=immersiveReader]")?.attr("x-data")
            ?: return super.pageListParse(document)

        val canRead = CAN_READ_REGEX.find(xData)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
        if (!canRead) {
            throw Exception("This chapter is locked. Log in via WebView and unlock this chapter to read.")
        }

        val baseLink = BASE_LINK_REGEX.find(xData)?.groupValues?.get(1) ?: "$baseUrl/storage/"
        val pagesJson = extractPagesJson(xData)
            ?: throw Exception("Failed to parse chapter pages")

        val pages = pagesJson.parseAs<List<PageDto>>()
        if (pages.isEmpty()) {
            throw Exception("This chapter is locked. Log in via WebView and unlock this chapter to read.")
        }

        return pages.mapIndexed { i, page ->
            val imageUrl = if (page.path.startsWith("http://") || page.path.startsWith("https://")) {
                page.path
            } else {
                "${baseLink.trimEnd('/')}/${page.path.trimStart('/')}"
            }
            Page(i, document.location(), imageUrl)
        }
    }

    private fun extractPagesJson(xData: String): String? {
        val startIndex = xData.indexOf("pages:").takeIf { it != -1 } ?: return null
        val afterPages = xData.substring(startIndex + 6).trimStart()
        if (!afterPages.startsWith("[")) return null

        var depth = 0
        for (i in afterPages.indices) {
            when (afterPages[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return afterPages.substring(0, i + 1)
                    }
                }
            }
        }
        return null
    }

    override fun getTypeList() = emptyMap<String, String>()

    companion object {
        private val CAN_READ_REGEX = """canRead\s*:\s*(true|false)""".toRegex()
        private val BASE_LINK_REGEX = """baseLink\s*:\s*['"]([^'"]+)['"]""".toRegex()
    }
}

@Serializable
class PageDto(
    val path: String,
)
