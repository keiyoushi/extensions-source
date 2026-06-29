package eu.kanade.tachiyomi.extension.en.mangamirai

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException

@Source
abstract class MangaMirai :
    HttpSource(),
    ConfigurableSource {
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()
    private val acceptHeaders = headersBuilder()
        .set("Accept", "*/*")
        .build()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 500 && request.url.pathSegments.last() == "product_content_images") {
                throw IOException("Log in via WebView and purchase this chapter to read.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }, GenreFilter(), TagFilter(), AuthorFilter(), PublisherFilter()))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 0 }, GenreFilter(), TagFilter(), AuthorFilter(), PublisherFilter()))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.firstInstance<SortFilter>()
        val genre = filters.firstInstance<GenreFilter>()
        val tag = filters.firstInstance<TagFilter>()
        val author = filters.firstInstance<AuthorFilter>()
        val publisher = filters.firstInstance<PublisherFilter>()
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("word", query)
            addQueryParameter("page", page.toString())
            addQueryParameter("order", sort.value)
            addQueryParameter("genre", genre.value)
            tag.state.forEach { addFilter("tags[]", it) }
            author.state.forEach { addFilter("authors[]", it) }
            addQueryParameter("publisher", publisher.value)
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card").map {
            SManga.create().apply {
                title = it.selectFirst("h3")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments.last())
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        GenreFilter(),
        TagFilter(),
        AuthorFilter(),
        PublisherFilter(),
    )

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/product_collections/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.select("h1 ~ table a[href^=/authors/]").joinToString { it.text() }
            description = document.selectFirst("span[data-product-collections--product-collection--long-description-accordion-target]")?.text()
            genre = document.select("div.hidden > .popular-categories a").joinToString { it.text() }
            status = if (document.selectFirst(".popular-categories a[href*=/tags/Completed]") != null) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("div.grid-cols-5.justify-between img")?.absUrl("src")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val url = "$baseUrl/product_collections/${manga.url}".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
            val response = client.newCall(GET(url, headers)).execute()
            val document = response.asJsoup()

            chapters += document.select("div.pb-5").mapNotNull {
                val isBought = it.selectFirst("a.gtm_read") != null
                val isFree = it.selectFirst("a.gtm_read_for_free") != null
                val isPreview = it.selectFirst("a.gtm_preview") != null
                val isLocked = !isBought && !isFree && !isPreview

                if (hideLocked && (isPreview || isLocked)) return@mapNotNull null

                val readerUrl = it.selectFirst("a[href*=/book_reader]")?.absUrl("href")?.toHttpUrl()?.pathSegments?.get(2)
                    ?: it.selectFirst("a.gtm_thumbnail_tap")!!.absUrl("href").toHttpUrl().pathSegments[3]

                SChapter.create().apply {
                    setUrlWithoutDomain(readerUrl)
                    name = buildString {
                        if (isPreview) {
                            append("🔒 (Preview) ")
                        } else if (isLocked) {
                            append("🔒 ")
                        }
                        append(it.selectFirst("h3 span.font-bold")!!.text())
                    }
                }
            }
            page++
        } while (document.selectFirst("a[rel=next]") != null)

        return Observable.just(chapters.reversed())
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/users/product_contents/${chapter.url}/book_reader"

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/users/product_contents/${chapter.url}/product_content_images".toHttpUrl().newBuilder()
            .addQueryParameter("start_page", "1")
            .addQueryParameter("limit", "10000")
            .build()
        return GET(url, acceptHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>()
        return result.records.map {
            Page(it.page, imageUrl = "${it.url}#${it.scrambleKey}")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
