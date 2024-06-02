package eu.kanade.tachiyomi.extension.en.hentairead

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Hentairead : Madara("HentaiRead", "https://hentairead.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

    override val versionId: Int = 2

    private val cdnHeaders = super.headersBuilder()
        .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .build()

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains("/wp-content/uploads/")) {
                return@addInterceptor chain.proceed(request.newBuilder().headers(cdnHeaders).build())
            }
            chain.proceed(request)
        }
        .build()

    override val mangaSubString = "hentai"
    override val fetchGenres = false

    override fun getFilterList() = FilterList()

    override fun searchLoadMoreRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl${searchPage(page)}".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "wp-manga")
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.c-tabs-item div.page-item-detail"

    override val mangaDetailsSelectorDescription = "div.post-sub-title.alt-title > h2"
    override val mangaDetailsSelectorAuthor = "div.post-meta.post-tax-wp-manga-artist > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorArtist = "div.post-meta.post-tax-wp-manga-artist > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorGenre = "div.post-meta.post-tax-wp-manga-genre > span.post-tags > a > span.tag-name"
    override val mangaDetailsSelectorTag = "div.post-meta.post-tax-wp-manga-tag > span.post-tags > a > span.tag-name"

    override val pageListParseSelector = "li.chapter-image-item > a > div.image-wrapper"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
        }
    }

    // From ManhwaHentai - modified
    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val pages = document.selectFirst("#chapter_preloaded_images")?.data()
            ?.substringAfter("chapter_preloaded_images = ")
            ?.substringBefore("],")
            ?.let { json.decodeFromString<List<PageDto>>("$it]") }
            ?: throw Exception("Failed to find page list. Non-English entries are not supported.")

        return pages.mapIndexed { idx, page ->
            Page(idx, document.location(), page.src)
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                },
            ),
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // There's like 2 non-English entries where this breaks
        val url = "${chapter.url}english/p/1/"

        if (url.startsWith("http")) {
            return GET(url, headers)
        }
        return GET(baseUrl + url, headers)
    }

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ").removeSuffix(",")
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            else -> element.attr("abs:src")
        }
    }
}

@Serializable
class PageDto(
    val src: String,
)
