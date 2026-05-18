package eu.kanade.tachiyomi.extension.ja.amebamanga

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
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class AmebaManga :
    HttpSource(),
    ConfigurableSource {
    override val name = "Ameba Manga"
    private val domain = "dokusho-ojikan.jp"
    override val baseUrl = "https://$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "https://api.$domain/dokusho-server"
    private val pageSize = 50
    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addNetworkInterceptor(CookieInterceptor(domain, ("AC" to "1")))
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 500 && request.url.encodedPath.contains("/browser/bookinfo/v3")) {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/rank/title/category".toHttpUrl().newBuilder()
            .addQueryParameter("ac", "1")
            .addQueryParameter("term_code", "monthly")
            .addQueryParameter("category", "page_type_all")
            .addQueryParameter("offset", ((page - 1) * pageSize).toString())
            .addQueryParameter("limit", pageSize.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<RankingResponse>()
        val mangas = result.titleRankResponses.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/release/book/recent".toHttpUrl().newBuilder()
            .addQueryParameter("ac", "1")
            .addQueryParameter("category", "page_type_all")
            .addQueryParameter("sort", "releaseDate")
            .addQueryParameter("offset", ((page - 1) * pageSize).toString())
            .addQueryParameter("limit", pageSize.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestResponse>()
        val mangas = result.books.map { it.toSManga() }
        val limit = response.request.url.queryParameter("limit")!!.toInt()
        val offset = response.request.url.queryParameter("offset")!!.toInt()
        val hasNextPage = offset + limit < result.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search/search/v2".toHttpUrl().newBuilder()
            .addQueryParameter("ac", "1")
            .addQueryParameter("word", query)
            .addQueryParameter("offset", ((page - 1) * pageSize).toString())
            .addQueryParameter("limit", pageSize.toString())
            .apply {
                addFilter("sort_key", filters.firstInstance<SortFilter>())
                addFilter("genre_id", filters.firstInstance<GenreFilter>())
                addFilter(filters.firstInstance<CategoryFilter>())
                addFilter("title_review_ave_from", filters.firstInstance<ReviewRatingFilter>())
                addFilter(filters.firstInstance<VolumeFilter>())
                addFilter("pub_id", filters.firstInstance<PublisherFilter>())
                addFilter("magazine_id", filters.firstInstance<MagazineFilter>())
                addFilter("book_price", filters.firstInstance<FreeFilter>(), "0")
                addFilter("price_type", filters.firstInstance<DiscountFilter>(), "discount")
                addFilter("tags_id", filters.firstInstance<CompletedFilter>(), "240")
                addFilter("meta_item_id", filters.firstInstance<AnimatedFilter>(), "3")
                addFilter("meta_item_id", filters.firstInstance<LiveActionFilter>(), "49")
                addFilter("has_serial", filters.firstInstance<HasSerialFilter>(), "true")
                addFilter("start_datetime_within_days", filters.firstInstance<ReleasedThisMonthFilter>(), "30")
            }.build()
        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        GenreFilter(),
        CategoryFilter(),
        ReviewRatingFilter(),
        VolumeFilter(),
        PublisherFilter(),
        MagazineFilter(),
        Filter.Separator(),
        Filter.Header("こだわり条件"),
        FreeFilter(),
        DiscountFilter(),
        CompletedFilter(),
        AnimatedFilter(),
        LiveActionFilter(),
        HasSerialFilter(),
        ReleasedThisMonthFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/titles/${manga.url}?ac=1", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series_list/series_id=${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/books/by_title/v3".toHttpUrl().newBuilder()
            .addQueryParameter("ac", "1")
            .addQueryParameter("title_id", manga.url)
            .addQueryParameter("sales_status", "IN_RESERVATION")
            .addQueryParameter("sales_status", "ON_SALE")
            .addQueryParameter("sort", "VOL_DESC")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "1000")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val books = response.parseAs<ChapterResponse>().books
        val ownedIds = fetchOwnedBookIds(books.map { it.id })
        return books
            .filter { !hideLocked || !it.isLockedFor(ownedIds) }
            .map { it.toSChapter(ownedIds) }
    }

    private fun fetchOwnedBookIds(bookIds: List<Int>): Set<Int>? {
        if (bookIds.isEmpty()) return null
        val url = "$apiUrl/user_books/me/by_book/v2".toHttpUrl().newBuilder().apply {
            bookIds.forEach { addQueryParameter("book_id", it.toString()) }
        }.build()

        return try {
            val ownershipResponse = client.newCall(GET(url, headers)).execute()
            if (!ownershipResponse.isSuccessful) return null
            ownershipResponse.parseAs<OwnedResponse>().userBooks.filter { it.isOwned }.map { it.bookId }.toSet()
        } catch (_: Exception) {
            null
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/index.html?cid=${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$apiUrl/browser/bookinfo/v3".toHttpUrl().newBuilder()
            .addQueryParameter("bookId", chapter.url)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>().result
        val guardianUrl = "${result.guardianServer}/${result.bookData.s3Key}"
        return if (result.bookData.imagedReflow) {
            parseNovelPages(result, guardianUrl)
        } else {
            parseMangaPages(result, guardianUrl)
        }
    }

    private fun parseNovelPages(result: ViewerResult, guardianUrl: String): List<Page> {
        val bookJsonUrl = "$guardianUrl/book.json".toHttpUrl().newBuilder()
            .query(result.signedParams)
            .build()

        val book = client.newCall(GET(bookJsonUrl, headers)).execute().parseAs<ReflowBook>()
        val profile = book.reflowData?.profiles?.find { it.id == "mincho_small" }
            ?: book.reflowData?.profiles?.firstOrNull()
            ?: throw Exception("No profile was found.")

        val key = result.keys?.jsonObject?.get(profile.id)?.jsonPrimitive?.content

        return (0 until profile.bookInfo.pageCount).map {
            Page(it, imageUrl = buildPageUrl(guardianUrl, "${profile.id}/${it + 1}.jpg", result.signedParams, key))
        }
    }

    private fun parseMangaPages(result: ViewerResult, guardianUrl: String): List<Page> {
        val keys = result.keys?.jsonArray?.map { it.jsonPrimitive.content } ?: throw Exception("No keys were found.")

        return keys.mapIndexed { i, key ->
            Page(i, imageUrl = buildPageUrl(guardianUrl, "${i + 1}.jpg", result.signedParams, key))
        }
    }

    private fun buildPageUrl(guardianUrl: String, path: String, signedParams: String, key: String?): String = "$guardianUrl/$path".toHttpUrl().newBuilder()
        .query(signedParams)
        .fragment(key)
        .build()
        .toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
