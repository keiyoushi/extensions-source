package eu.kanade.tachiyomi.extension.all.misskon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MissKon : KeiSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(10, 1.seconds) { it.host == baseUrlHost }

    private fun mangaFromElement(element: Element): SManga {
        val titleEL = element.selectFirst(".post-box-title")!!
        return SManga.create().apply {
            title = titleEL.text()
            thumbnail_url = element.selectFirst(".post-thumbnail img")?.absUrl("data-src")
            setUrlWithoutDomain(titleEL.selectFirst("a")!!.absUrl("href"))
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // region popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/top3/").asJsoup()
        val mangas = document.select("article.item-list").map { mangaFromElement(it) }
        return MangasPage(mangas, false)
    }
    // endregion

    // region latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/page/$page").asJsoup()
        val mangas = document.select("article.item-list").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst(".current + a.page") != null
        return MangasPage(mangas, hasNextPage)
    }
    // endregion

    // region Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val filter = filters.firstInstance<SourceCategorySelector>()
        val url = filter.selectedCategory?.let {
            "$baseUrl${it.url}"
                .toHttpUrl()
        } ?: run {
            "$baseUrl/page/$page/".toHttpUrl().newBuilder()
                .addEncodedQueryParameter("s", query)
                .build()
        }

        val document = client.get(url).asJsoup()
        val mangas = document.select("article.item-list").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("div.content > div.pagination > span.current + a") != null
        return MangasPage(mangas, hasNextPage)
    }
    // endregion

    // region Details
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedManga = parseMangaDetails(document)
        val updatedChapters = parseChapterList(document, manga.url)
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseMangaDetails(document: Document): SManga {
        val postInnerEl = document.selectFirst("article > .post-inner")!!
        return SManga.create().apply {
            title = postInnerEl.select(".post-title").text()
            genre = postInnerEl.select(".post-tag > a").joinToString { it.text() }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun parseChapterList(document: Document, mangaUrl: String): List<SChapter> {
        val dateUploadStr = document.selectFirst(".entry img")?.absUrl("data-src")
            ?.let { url ->
                FULL_DATE_REGEX.find(url)?.groupValues?.get(1)
                    ?: YEAR_MONTH_REGEX.find(url)?.groupValues?.get(1)?.let { "$it/01" }
            }
        val dateUpload = FULL_DATE_FORMAT.tryParse(dateUploadStr)
        val maxPage = document.select("div.page-link:first-of-type a.post-page-numbers").last()?.text()?.toInt() ?: 1
        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                setUrlWithoutDomain("$mangaUrl/$page")
                name = "Page $page"
                date_upload = dateUpload
            }
        }
    }

    /* Related titles */
    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return document.select(".content > .yarpp-related a.yarpp-thumbnail").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title")
                thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
            }
        }
    }
    // endregion

    // region Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document
            .select("div.post-inner > div.entry > p > img")
            .mapIndexed { i, imgEl -> Page(i, imageUrl = imgEl.absUrl("data-src")) }
    }
    // endregion

    /* Filters */
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTE: Unable to further search in the category!"),
        Filter.Separator(),
        SourceCategorySelector.create(),
    )

    companion object {
        private val FULL_DATE_REGEX = Regex("""/(\d{4}/\d{2}/\d{2})/""")
        private val YEAR_MONTH_REGEX = Regex("""/(\d{4}/\d{2})/""")
        private val FULL_DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    }
}
