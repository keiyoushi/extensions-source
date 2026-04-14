package eu.kanade.tachiyomi.multisrc.mangawork

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class MangaWork(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    protected open val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    protected open val seriesPath = "series"
    protected open val mangaPath = "manga"
    protected open val adminAjaxPath = "wp-admin/admin-ajax.php"

    protected open val searchQueryParam = "title"
    protected open val orderQueryParam = "order"
    protected open val statusQueryParam = "status"
    protected open val typeQueryParam = "type"
    protected open val genreQueryParam = "genre[]"
    protected open val yearQueryParam = "years[]"

    protected open val popularOrderValue = "popular"
    protected open val latestOrderValue = "update"
    protected open val searchOrderValue = "title"
    protected open val chapterAjaxAction = "load_chapters"
    protected open val defaultChapterOrder = "DESC"
    protected open val defaultChapterCount = "1000"

    protected open val mangaEntrySelector = "div.w-full.h-full:has(a[href*='/$mangaPath/'])"
    protected open val mangaEntryAnchorSelector = "a[href*='/$mangaPath/']"
    protected open val mangaEntryTitleSelector = "h1"
    protected open val mangaEntryThumbnailSelector = "img"
    protected open val listNextPageSelector = ".pagination .page-numbers.current + a[href]"

    protected open val detailsTitleSelector = "h1.text-4xl.font-bold.mb-2"
    protected open val detailsThumbnailSelector = "img[itemprop=image], [itemprop=image] img"
    protected open val detailsGenreSelector = "[itemprop=genre]"
    protected open val detailsDescriptionSelector = "div.text-base.leading-relaxed.mb-6.text-muted-foreground"
    protected open val detailsInfoItemSelector = "div.grid.grid-cols-2.gap-4.text-sm.text-gray-600.mb-6 > div"
    protected open val detailsInfoLabelSelector = "strong"
    protected open val detailsInfoValueSelector = "p"
    protected open val detailsAuthorLabel = "Autor(es)"

    protected open val chapterContainerSelector = "#chapter_list.chapter_list_container"
    protected open val chapterEntrySelector = "li"
    protected open val chapterLinkSelector = "a[href]"
    protected open val chapterNameSelector = "span.m-0, span.line-clamp-1"
    protected open val chapterNextPageSelector = "button.load-chapters[data-paged]"
    protected open val chapterNumberRegex = Regex("""(\d+(?:[.,]\d+)?)""")

    protected open val pageImageSelector = "div.reader-area img#imagech, div.reader-area img[src*='/manga_auto_capitulos/']"
    protected open val pageImageRegex = Regex(""""image"\s*:\s*"([^"]+)"""")

    protected open val orderFilterTitle = "Sort by"
    protected open val statusFilterTitle = "Status"
    protected open val typeFilterTitle = "Type"
    protected open val genreFilterTitle = "Genre"
    protected open val yearFilterTitle = "Years"

    protected open fun getOrderFilterOptions(): Array<Pair<String, String>> = emptyArray()

    protected open fun getStatusFilterOptions(): Array<Pair<String, String>> = emptyArray()

    protected open fun getTypeFilterOptions(): Array<Pair<String, String>> = emptyArray()

    protected open fun getGenreFilterOptions(): Array<Pair<String, String>> = emptyArray()

    protected open fun getYearFilterOptions(): Array<Pair<String, String>> = emptyArray()

    // ==========Popular==========

    override fun popularMangaRequest(page: Int): Request = buildSeriesRequest(
        page = page,
        query = "",
        filters = FilterList(),
        defaultOrderValue = popularOrderValue,
    )

    override fun popularMangaSelector(): String = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = searchMangaNextPageSelector()

    // ==========Latest==========

    override fun latestUpdatesRequest(page: Int): Request = buildSeriesRequest(
        page = page,
        query = "",
        filters = FilterList(),
        defaultOrderValue = latestOrderValue,
    )

    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = searchMangaNextPageSelector()

    // ==========Search==========

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = buildSeriesRequest(
        page = page,
        query = query,
        filters = filters,
        defaultOrderValue = searchOrderValue,
    )

    override fun searchMangaSelector(): String = mangaEntrySelector

    override fun searchMangaFromElement(element: Element): SManga {
        val anchor = element.selectFirst(mangaEntryAnchorSelector)!!

        return SManga.create().apply {
            title = anchor.attr("title").ifBlank {
                element.selectFirst(mangaEntryTitleSelector)!!.text()
            }
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = anchor.selectFirst(mangaEntryThumbnailSelector)?.toImageUrl()
        }
    }

    override fun searchMangaNextPageSelector(): String? = listNextPageSelector

    private fun buildSeriesRequest(
        page: Int,
        query: String,
        filters: FilterList,
        defaultOrderValue: String,
    ): Request {
        var orderValue = defaultOrderValue
        var statusValue = ""
        var typeValue = ""
        val extraQueryParameters = mutableListOf<Pair<String, String>>()

        filters.filterIsInstance<MangaWorkQueryFilter>().forEach { filter ->
            when (filter) {
                is MangaWorkOrderFilter -> orderValue = filter.selectedValue
                is MangaWorkStatusFilter -> statusValue = filter.selectedValue
                is MangaWorkTypeFilter -> typeValue = filter.selectedValue
                else -> filter.appendQueryParameters(extraQueryParameters)
            }
        }

        val url = buildSeriesUrl(page).toHttpUrl().newBuilder()
            .addQueryParameter(searchQueryParam, query)
            .addQueryParameter(orderQueryParam, orderValue)
            .addQueryParameter(statusQueryParam, statusValue)
            .addQueryParameter(typeQueryParam, typeValue)
            .apply {
                extraQueryParameters.forEach { (name, value) ->
                    addQueryParameter(name, value)
                }
            }
            .build()

        return GET(url, headers)
    }

    protected open fun buildSeriesUrl(page: Int): String = buildString {
        append(baseUrl.trimEnd('/'))
        append('/')
        append(seriesPath.trim('/'))
        append('/')

        if (page > 1) {
            append("page/")
            append(page)
            append('/')
        }
    }

    // ==========Details==========

    override fun mangaDetailsParse(document: Document): SManga {
        val genres = document.select(detailsGenreSelector)

        return SManga.create().apply {
            title = document.selectFirst(detailsTitleSelector)!!.text()
            thumbnail_url = document.selectFirst(detailsThumbnailSelector)?.toImageUrl()
            author = document.findInfoValue(detailsAuthorLabel)
            genre = genres.joinToString { it.text() }.takeIf { it.isNotEmpty() }
            description = document.selectFirst(detailsDescriptionSelector)?.text()
            status = parseStatus(genres.firstOrNull()?.previousElementSibling()?.text())
        }
    }

    protected open fun Document.findInfoValue(label: String): String? = select(detailsInfoItemSelector)
        .firstOrNull { item ->
            item.selectFirst(detailsInfoLabelSelector)?.text() == label
        }
        ?.selectFirst(detailsInfoValueSelector)
        ?.text()
        ?.takeIf { it.isNotEmpty() }

    protected open fun parseStatus(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
        "publishing", "ongoing", "em andamento" -> SManga.ONGOING
        "finished", "completed", "concluído", "concluido", "finalizado" -> SManga.COMPLETED
        "on hold", "on-hold", "hiatus", "em hiato" -> SManga.ON_HIATUS
        "cancelled", "canceled", "cancelado" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ==========Chapters==========

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterContainer = document.selectFirst(chapterContainerSelector)
        val chapters = document.chapterElements().map(::chapterFromElement).toMutableList()

        if (chapterContainer == null) {
            return chapters
        }

        val postId = chapterContainer.attr("data-post-id").ifBlank { return chapters }
        val count = chapterContainer.attr("data-count").ifBlank { defaultChapterCount }
        val requestedPages = mutableSetOf<String>()
        var currentPage = 1
        var nextButton = document.findNextChapterPageButton(currentPage)

        while (nextButton != null) {
            val page = nextButton.attr("data-paged")
                .takeIf { it.isNotBlank() && requestedPages.add(it) }
                ?: break

            val order = nextButton.attr("data-order").ifBlank { defaultChapterOrder }

            client.newCall(
                chapterListPageRequest(
                    referer = response.request.url.toString(),
                    postId = postId,
                    count = count,
                    page = page,
                    order = order,
                ),
            ).execute().use { chapterPageResponse ->
                val chapterPageDocument = chapterPageResponse.asJsoup()
                chapters += chapterPageDocument.chapterElements().map(::chapterFromElement)
                currentPage = page.toIntOrNull() ?: currentPage
                nextButton = chapterPageDocument.findNextChapterPageButton(currentPage)
            }
        }

        return chapters.distinctBy(SChapter::url)
    }

    override fun chapterListSelector(): String = chapterEntrySelector

    override fun chapterFromElement(element: Element): SChapter {
        val chapterAnchor = element.selectFirst(chapterLinkSelector)!!
        val chapterName = element.chapterNameText()
            ?: chapterAnchor.ownText().ifBlank { chapterAnchor.text() }

        return SChapter.create().apply {
            name = chapterName
            chapter_number = chapterNumberRegex.find(chapterName)
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toFloatOrNull()
                ?: -1f
            date_upload = parseChapterDate(element.chapterDateText())
            setUrlWithoutDomain(chapterAnchor.absUrl("href"))
        }
    }

    protected open fun chapterListPageRequest(
        referer: String,
        postId: String,
        count: String,
        page: String,
        order: String,
    ): Request {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", chapterAjaxAction)
            .addFormDataPart("post_id", postId)
            .addFormDataPart("count", count)
            .addFormDataPart("paged", page)
            .addFormDataPart("order", order)
            .build()

        return POST(
            url = buildAdminAjaxUrl(),
            headers = headersBuilder()
                .set("Referer", referer)
                .add("Origin", baseUrl)
                .add("Accept", "*/*")
                .build(),
            body = body,
        )
    }

    protected open fun buildAdminAjaxUrl(): String = "${baseUrl.trimEnd('/')}/${adminAjaxPath.trimStart('/')}"

    protected open fun Document.chapterElements(): List<Element> = selectFirst(chapterContainerSelector)
        ?.select(chapterEntrySelector)
        .orEmpty()

    protected open fun Document.findNextChapterPageButton(currentPage: Int): Element? {
        for (button in select(chapterNextPageSelector)) {
            val page = button.attr("data-paged").toIntOrNull() ?: continue

            if (page > currentPage) {
                return button
            }
        }

        return null
    }

    protected open fun Element.chapterDateText(): String? = select("span")
        .lastOrNull()
        ?.text()
        ?.takeIf { it.isNotEmpty() }

    protected open fun Element.chapterNameText(): String? {
        val chapterNameElement = selectFirst(chapterNameSelector) ?: return null
        val chapterName = chapterNameElement.ownText().ifBlank { chapterNameElement.text() }

        return chapterName.takeIf { it.isNotEmpty() }
    }

    protected open fun parseChapterDate(date: String?): Long = date?.let(chapterDateFormat::tryParse) ?: 0L

    // ==========Pages==========

    override fun pageListParse(document: Document): List<Page> {
        val imageUrls = document.select(pageImageSelector)
            .mapNotNull { element -> element.toImageUrl() }

        if (imageUrls.isNotEmpty()) {
            return imageUrls.mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
        }

        val regexImageUrls = pageImageRegex.findAll(document.html())
            .map { result ->
                result.groupValues[1].replace("\\/", "/")
            }
            .toList()

        if (regexImageUrls.isEmpty()) {
            throw IOException("No pages found")
        }

        return regexImageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    protected open fun Element.toImageUrl(): String? = sequenceOf(
        absUrl("src"),
        absUrl("data-src"),
        absUrl("data-lazy-src"),
    ).firstOrNull { it.isNotEmpty() }

    // ==========Filters==========

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        getOrderFilterOptions()
            .takeIf { it.isNotEmpty() }
            ?.let {
                filters += MangaWorkOrderFilter(
                    title = orderFilterTitle,
                    queryParam = orderQueryParam,
                    options = it,
                    defaultValue = searchOrderValue,
                )
            }

        getStatusFilterOptions()
            .takeIf { it.isNotEmpty() }
            ?.let {
                filters += MangaWorkStatusFilter(
                    title = statusFilterTitle,
                    queryParam = statusQueryParam,
                    options = it,
                )
            }

        getTypeFilterOptions()
            .takeIf { it.isNotEmpty() }
            ?.let {
                filters += MangaWorkTypeFilter(
                    title = typeFilterTitle,
                    queryParam = typeQueryParam,
                    options = it,
                )
            }

        getGenreFilterOptions()
            .takeIf { it.isNotEmpty() }
            ?.let {
                filters += MangaWorkGenreFilter(
                    title = genreFilterTitle,
                    queryParam = genreQueryParam,
                    options = it,
                )
            }

        getYearFilterOptions()
            .takeIf { it.isNotEmpty() }
            ?.let {
                filters += MangaWorkYearFilter(
                    title = yearFilterTitle,
                    queryParam = yearQueryParam,
                    options = it,
                )
            }

        return FilterList(filters)
    }
}
