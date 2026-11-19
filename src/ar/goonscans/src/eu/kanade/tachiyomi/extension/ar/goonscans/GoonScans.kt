package eu.kanade.tachiyomi.extension.ar.goonscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class GoonScans : MangaThemesia() {
    override val popularFilter by lazy { FilterList(OrderByFilter("top_rated", emptyArray())) }
    override val latestFilter by lazy { FilterList(OrderByFilter("latest_chapter", emptyArray())) }

    override val datePattern = "yyyy.dd.MM"

    override fun searchMangaNextPageSelector() = ".next-btn"

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("title")
        if (page > 1) {
            addPathSegment("page")
            addPathSegment(page.toString())
        }
        addPathSegment("")
        addQueryParameter(
            "orderby",
            filters.firstInstance<OrderByFilter>().name,
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val term = query.trim()
        if (term.isEmpty()) return super.getSearchMangaList(page, query, filters)
        // The site's live search needs at least two characters
        if (term.length < 2) return MangasPage(emptyList(), false)

        val searchUrl = "$baseUrl/search/"
        val nonce = client.get(searchUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("script:containsData(glsa_live_search)")!!.data()
            .let { NONCE_REGEX.find(it)!!.groupValues[1] }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", "glsa_live_search")
            .addFormDataPart("term", term)
            .addFormDataPart("nonce", nonce)
            .build()
        val html = client.post("$baseUrl/wp-admin/admin-ajax.php", Fetch.XHR.from(searchUrl), body)
            .parseAs<LiveSearchDto>().data.html
        val mangas = html.asJsoup(baseUrl).select("a.result-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".result-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = ".cover-wrapper"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val img = element.selectFirst("img")!!
        thumbnail_url = img.imgAttr()
        title = img.attrOrNull("alt")!!
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override val seriesDetailsSelector = ".webtoon-container"
    override val seriesTitleSelector = ".webtoon-title"
    override val seriesArtistSelector = ".creators-list a[href*=/artist/]:matchesOwn(\\p{L})"
    override val seriesAuthorSelector = ".creators-list a[href*=/writer/]:matchesOwn(\\p{L})"
    override val seriesTypeSelector = ".webtoon-type a"
    override val seriesThumbnailSelector = ".webtoon-cover"
    override val seriesDescriptionSelector = ".description-content"
    override val seriesGenreSelector = ".genre-tags a"
    override val seriesStatusSelector = ".cover-status-badge"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(seriesDescriptionSelector))
            .altTitles(listOfNotNull(document.selectFirst(".webtoon-alternate-names")?.attrOrNull("data-full-text")))
            .build()
    }

    override fun chapterListSelector() = "ul.chapter-list li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = element.selectFirst(".chapter-number")!!.text()
        date_upload = element.selectFirst(".chapter-date")?.text().parseChapterDate()
    }

    override val supportsFilterFetching = false
    override fun getFilterList(data: JsonElement?) = FilterList()

    companion object {
        private val NONCE_REGEX = Regex("""'nonce',\s*'([^']+)'""")
    }
}
