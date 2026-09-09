package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.plusAssign
import kotlin.time.Duration.Companion.seconds

@Source
abstract class XXXYaoi : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Upgrade-Insecure-Requests", "1")
        .set("Sec-GPC", "1")
        .set("Sec-Fetch-User", "?1")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Dest", "document")
        .set("Priority", "u=0, i")
        .set("Pragma", "no-cache")

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaSubString = "bl"

    override val mangaDetailsSelectorTitle = ".xyaoi-main-title, h1"
    override val mangaDetailsSelectorAuthor = "a[href*=author]"
    override val mangaDetailsSelectorArtist = "a[href*=artist]"
    override val mangaDetailsSelectorStatus = "span:contains(status) + span"
    override val mangaDetailsSelectorDescription = "[class*=synopsis]"

    override val statusFilterOptions: Map<String, String> =
        mapOf(
            intl["status_filter_completed"] to "end",
        )

    override fun searchMangaSelector() = ".page-item-detail.manga"

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        loop@ for (filter in filters) {
            when (filter) {
                is StatusFilter -> {
                    filter.state.firstOrNull { it.state }?.let {
                        url.addPathSegment(it.name)
                        break@loop
                    }
                }

                is GenreOptions -> {
                    val selected = filter.selected()
                    if (selected.isNotBlank()) {
                        url.addPathSegment("genero")
                            .addPathSegment(selected)
                        break@loop
                    }
                }

                else -> {}
            }
        }

        url.addPathSegments(searchPage(page))
        return GET(url.build(), headers)
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("div > span:nth-child(1)")!!.text()
        date_upload = parseChapterDate(element.selectFirst("div:has(> span:nth-child(1)) + div")?.text())
        setUrlWithoutDomain(element.selectFirst(chapterUrlSelector)!!.absUrl("href"))
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters: MutableList<Filter<out Any>> = mutableListOf(
            StatusFilter(
                title = intl["status_filter_title"],
                status = statusFilterOptions.map { Tag(it.key, it.value) },
            ),
        )

        if (genresList.isNotEmpty()) {
            val options: Array<Pair<String, String>> = arrayOf("Todos" to "") + genresList.map { it.name to it.id }.toTypedArray()
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_filter_header"]),
                GenreOptions(
                    displayName = intl["genre_filter_title"],
                    vals = options,
                ),
            )
        } else if (fetchGenres) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    override fun pageListParse(document: Document): List<Page> = getPages(document)
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
        .takeUnless(List<Page>::isEmpty)
        ?: return super.pageListParse(document)

    private fun getPages(document: Document): List<String> {
        val script = document.selectFirst("script:containsData(page-break)")?.data() ?: return emptyList()
        val key = PAGE_KEY_REGEX.find(script)!!.groupValues.last()
        val attr = PAYLOAD_ATTR_REGEX.find(script)!!.groupValues.last()

        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val encrypted = document.selectFirst("[$attr]")!!.attr(attr)

        val decodedBytes = Base64.decode(encrypted, Base64.DEFAULT)
        val decryptedBytes = ByteArray(decodedBytes.size) { i ->
            (decodedBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return String(decryptedBytes, Charsets.UTF_8).parseAs<List<String>>()
    }

    class GenreOptions(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun selected() = vals[state].second
    }

    companion object {
        private val PAGE_KEY_REGEX = """key\s+=\s+.([^']+)""".toRegex()
        private val PAYLOAD_ATTR_REGEX = """=\s+?'(data[^']+)""".toRegex()
    }
}
