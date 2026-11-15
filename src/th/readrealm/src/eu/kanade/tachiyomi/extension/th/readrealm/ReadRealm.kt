package eu.kanade.tachiyomi.extension.th.readrealm

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class ReadRealm :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3) { it.host == API_HOST }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.POPULAR)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.UPDATE)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = API_URL.newBuilder()
            .addPathSegment("getSearchBook")
            .addQueryParameter("searchText", query.trim())
            .addQueryParameter("searchType", "comic")
            .addQueryParameter("searchCategory", filters.firstInstanceOrNull<CategoryFilter>()?.value.orEmpty())
            .addQueryParameter("searchStatus", filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty())
            .addQueryParameter("searchRating", filters.firstInstanceOrNull<RatingFilter>()?.value.orEmpty())
            .addQueryParameter("searchComicType", "")
            .addQueryParameter("searchSort", filters.firstInstance<SortFilter>().value)
            .addQueryParameter("page", page.toString())
            .build()
        val books = client.get(url, Fetch.XHR).parseAs<List<Book>>()
        return MangasPage(books.map { it.toSManga() }, books.size == PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val id = when {
            segments.size == 2 && segments[0] == "comic" -> segments[1]
            segments.size == 3 && segments[0] == "comic" && segments[1] == "chapter" ->
                fetchChapterData(getChapterUrl(SChapter.create().apply { this.url = segments[2] }))?.contentDetails?.bookId
            else -> null
        } ?: return null
        return fetchBook(id).toSManga().apply { initialized = true }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size != 2 || url.pathSegments[0] != "profile") {
            return super.getMangasByUrl(url, page)
        }
        val profileUrl = API_URL.newBuilder("/users/profile/getProfileInfomation")!!
            .addPathSegment(url.pathSegments[1])
            .build()
        val books = client.get(profileUrl, Fetch.XHR).parseAs<Profile>().bookData.comics
        return MangasPage(books.map { it.toSManga() }, false)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val book = fetchBook(manga.url)
        return SMangaUpdate(book.toSManga(), if (fetchChapters) getChapters(book) else chapters)
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val book = fetchBook(manga.url)
        val url = API_URL.newBuilder()
            .addPathSegment("getBookInterestedList")
            .addQueryParameter("book_type", "comic")
            .addQueryParameter("book_id", book.id)
            .addQueryParameter("book_category", book.mainCategory)
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<Book>>().map { it.toSManga() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comic/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val content = fetchChapterData(getChapterUrl(chapter))!!.chapterContent ?: return emptyList()
        return content.mapIndexed { index, file ->
            Page(index, imageUrl = "$IMAGE_URL/${file.fileName}")
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        CategoryFilter(),
        StatusFilter(),
        RatingFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_PAID
            title = "แสดงตอนที่ต้องใช้เหรียญ"
            summary = "แสดงตอนที่ขายด้วย ReadCoin พร้อมเครื่องหมาย 🔒 ตอนเหล่านี้อ่านในแอปไม่ได้"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private suspend fun fetchBook(id: String): Book {
        val url = API_URL.newBuilder()
            .addPathSegment("getBookHomePage")
            .addQueryParameter("book_type", "comic")
            .addQueryParameter("book_id", id)
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    private suspend fun getChapters(book: Book): List<SChapter> = coroutineScope {
        val showPaid = preferences.getBoolean(PREF_SHOW_PAID, false)
        val sections = (0 until book.chapterCount step SECTION_SIZE).reversed().map { offset ->
            async {
                val url = API_URL.newBuilder()
                    .addPathSegment("getListChaptersSectionPage")
                    .addQueryParameter("book_type", "comic")
                    .addQueryParameter("book_id", book.id)
                    .addQueryParameter("start_index", (offset + 1).toString())
                    .addQueryParameter("sort", "desc")
                    .build()
                client.get(url, Fetch.XHR).parseAs<ChapterSection>().data
            }
        }
        val rows = sections.awaitAll().flatten()
        rows.mapIndexedNotNull { index, row ->
            if (row.isLocked && !showPaid) return@mapIndexedNotNull null
            row.toSChapter(if (index == 0) book.lastUpdate else null)
        }
    }

    private suspend fun fetchChapterData(url: String): ChapterData? = client.get(url, Fetch.DOCUMENT)
        .extractNextJs<ChapterData> { it is JsonObject && "content_details" in it && "chapter_content" in it }

    companion object {
        private const val API_HOST = "api.readrealm.co"
        private val API_URL = "https://$API_HOST/reader/book".toHttpUrl()
        const val IMAGE_URL = "https://image.readrealm.co"
        private const val PAGE_SIZE = 20
        private const val SECTION_SIZE = 50
        private const val PREF_SHOW_PAID = "show_paid_chapters"
    }
}
