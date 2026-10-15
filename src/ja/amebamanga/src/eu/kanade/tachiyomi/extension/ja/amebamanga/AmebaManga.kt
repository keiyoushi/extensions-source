package eu.kanade.tachiyomi.extension.ja.amebamanga

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.array
import keiyoushi.utils.firstInstance
import keiyoushi.utils.get
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class AmebaManga :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/dokusho-server"
    private val pageSize = 50
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addCookie("AC" to "1")
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 500 && request.url.encodedPath.contains("/browser/bookinfo/v3")) {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * pageSize
        val url = "$apiUrl/rank/title/category".toHttpUrl().newBuilder()
            .addQueryParameter("ac", "1")
            .addQueryParameter("term_code", "monthly")
            .addQueryParameter("category", "page_type_all")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", pageSize.toString())
            .build()

        return client.get(url).toMangasPage(offset)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * pageSize
        val url = "$apiUrl/release/book/recent".toHttpUrl().newBuilder()
            .addQueryParameter("ac", "1")
            .addQueryParameter("category", "page_type_all")
            .addQueryParameter("sort", "releaseDate")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", pageSize.toString())
            .build()

        return client.get(url).toMangasPage(offset)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * pageSize
        val url = "$apiUrl/search/search/v2".toHttpUrl().newBuilder()
            .addQueryParameter("ac", "1")
            .addQueryParameter("word", query)
            .addQueryParameter("offset", offset.toString())
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

        return client.get(url).toMangasPage(offset)
    }

    private fun Response.toMangasPage(offset: Int): MangasPage {
        val result = this.parseAs<TitleListResponse>()
        val mangas = result.titles.map { it.toSManga() }
        val hasNextPage = offset + pageSize < result.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series_list/series_id=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/titles/${manga.url}?ac=1").parseAs<DetailsResponse>().toSManga()
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val url = "$apiUrl/books/by_title/v3".toHttpUrl().newBuilder()
                .addQueryParameter("ac", "1")
                .addQueryParameter("title_id", manga.url)
                .addQueryParameter("sales_status", "IN_RESERVATION")
                .addQueryParameter("sales_status", "ON_SALE")
                .addQueryParameter("sort", "VOL_DESC")
                .addQueryParameter("offset", "0")
                .addQueryParameter("limit", "1000")
                .build()

            val books = client.get(url).parseAs<ChapterResponse>().books
            val lockedBooks = books.filter { it.isLocked }
            val ownedUrl = "$apiUrl/user_books/me/by_book/v2".toHttpUrl().newBuilder()
                .apply { lockedBooks.forEach { addQueryParameter("book_id", it.id.toString()) } }
                .build()

            val isLoggedIn = client.cookieJar.loadForRequest(ownedUrl).any { it.name == "AM_SESSION" }
            val ownedIds = if (lockedBooks.isEmpty() || !isLoggedIn) {
                emptySet()
            } else {
                val ownedResponse = client.get(ownedUrl, ensureSuccess = false)
                if (ownedResponse.isSuccessful) {
                    ownedResponse.parseAs<OwnedResponse>().userBooks.filter { it.isOwned }.map { it.bookId }.toSet()
                } else {
                    ownedResponse.close()
                    emptySet()
                }
            }

            books.filter { !hideLocked || !it.isLockedFor(ownedIds) }.map { it.toSChapter(ownedIds) }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/index.html?cid=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/browser/bookinfo/v3".toHttpUrl().newBuilder()
            .addQueryParameter("bookId", chapter.url)
            .build()

        val result = client.get(url).parseAs<ViewerResponse>().result
        val guardianUrl = "${result.guardianServer}/${result.bookData.s3Key}"

        if (!result.bookData.imagedReflow) {
            return result.keys!!.array.mapIndexed { i, key ->
                Page(i, imageUrl = buildPageUrl(guardianUrl, "${i + 1}.jpg", result.signedParams, key.string))
            }
        }

        val bookUrl = "$guardianUrl/book.json".toHttpUrl().newBuilder()
            .encodedQuery(result.signedParams)
            .build()

        val profiles = client.get(bookUrl).parseAs<ReflowBook>().reflowData.profiles
        val profile = profiles.find { it.id == "mincho_medium" } ?: profiles.first()
        val key = result.keys[profile.id]!!.string

        return List(profile.bookInfo.pageCount) {
            Page(it, imageUrl = buildPageUrl(guardianUrl, "${profile.id}/${it + 1}.jpg", result.signedParams, key))
        }
    }

    private fun buildPageUrl(guardianUrl: String, path: String, signedParams: String, key: String): String = "$guardianUrl/$path".toHttpUrl().newBuilder()
        .encodedQuery(signedParams)
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

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
