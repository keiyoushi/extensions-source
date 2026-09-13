package eu.kanade.tachiyomi.extension.ja.mechacomic

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class MechaComic :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "$baseUrl/api/v1"
    private val cdnUrl = "https://c.$domain/images"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie { listOf("_taste" to "all", "_confirmed_adult" to "1") }
        addInterceptor(ImageInterceptor())
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/sales_rankings/current".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url).parseAs<RankingResponse>()
        val mangas = result.rankingBooks.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/books/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, desktopHeaders).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstance<GenreFilter>()
        val sortFilter = filters.firstInstance<SortFilter>()
        val completedFilter = filters.firstInstance<CompletedFilter>()
        val booksPath = if (genreFilter.isAdult) "r/books" else "books"
        val url = "$baseUrl/$booksPath".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("text", query)
            .apply {
                genreFilter.value.takeIf { it.isNotEmpty() }?.let {
                    addQueryParameter("genre", it)
                }
                sortFilter.value.takeIf { it.isNotEmpty() }?.let {
                    addQueryParameter("sort", it)
                }
                if (completedFilter.state) {
                    addQueryParameter("filter[]", "completed")
                }
            }.build()
        return client.get(url, desktopHeaders).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val adult = this.request.url.pathSegments.first() == "r"
        val document = this.asJsoup()
        val mangas = document.select("li.p-bookList_item").map {
            SManga.create().apply {
                val link = it.selectFirst("dt.p-book_title a")!!
                val path = link.absUrl("href").toHttpUrl()
                title = link.text()
                thumbnail_url = it.selectFirst("div.p-book_jacket img[class^=jacket_image]")?.absUrl("src")
                url = if (adult) path.pathSegments[2] else path.pathSegments[1]
                if (adult) {
                    memo = buildJsonObject {
                        put("adult", "r")
                    }
                }
            }
        }
        val hasNextPage = document.selectFirst("a.next_page") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String {
        val adult = manga.memo.getStringOrNull("adult") == "r"
        return if (adult) "$baseUrl/r/books/${manga.url}" else "$baseUrl/books/${manga.url}"
    }

    // TODO: volumes
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) {
            return SMangaUpdate(manga, chapters)
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val pageUrl = getMangaUrl(manga).toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
            val document = client.get(pageUrl, desktopHeaders).asJsoup()

            if (fetchDetails && page == 1) {
                manga.apply {
                    title = document.selectFirst(".p-bookInfo_title h1")!!.text()
                    author = document.select("#js-anchor-defList dt:contains(作家) + dd .p-sepList_item a").joinToString { it.text() }
                    description = document.selectFirst(".p-bookInfo_summary p")?.text()
                    genre = document.select("#js-anchor-defList dt:contains(ジャンル) + dd a").joinToString { it.text() }
                    status = if (document.selectFirst(".c-tag-completed") != null) SManga.COMPLETED else SManga.ONGOING
                    thumbnail_url = document.selectFirst("img.jacket_image_l")?.absUrl("src")
                }
            }

            if (fetchChapters) {
                val chapterItems = document.select("ol.p-chapterList li.p-chapterList_item:has(dl.p-chapterList_txtArea)")
                chapterList += if (chapterItems.isNotEmpty()) {
                    chapterItems.mapNotNull {
                        val btn = it.selectFirst(".p-chapterList_btnArea a.p-btn-chapter") ?: return@mapNotNull null
                        val isLocked = ((btn.text() != "無料" && btn.absUrl("href").toHttpUrl().pathSegments[0] != "free_chapters") && btn.text() != "読む" && btn.absUrl("href").toHttpUrl().pathSegments[0] != "chapters") || btn.absUrl("href").toHttpUrl().pathSegments[0] != "free_chapters"
                        if (hideLocked && isLocked) return@mapNotNull null

                        SChapter.create().apply {
                            val lock = if (isLocked) "🔒 " else ""
                            val href = btn.absUrl("href").toHttpUrl()
                            setUrlWithoutDomain(href.pathSegments[1])
                            if (href.pathSegments[0] == "free_chapters") {
                                memo = buildJsonObject {
                                    put("free", "1")
                                    put("code", href.pathSegments[3])
                                }
                            }
                            name = lock + it.selectFirst("dd.p-chapterList_name")!!.text()
                            chapter_number = it.selectFirst("dt.p-chapterList_no")!!.text().replace("話", "").trim().toFloatOrNull() ?: -1f
                        }
                    }
                } else {
                    document.select("ol.p-volumeList li.p-volumeList_item:has(dl.p-volumeInfo_body)").mapNotNull {
                        val link = it.selectFirst("dt.p-volumeList_no a")!!
                        val href = link.absUrl("href").toHttpUrl().pathSegments
                        val buyHref = it.selectFirst(".p-volumeInfo_btnList_item-button2 a.p-btn-volume")?.absUrl("href")?.toHttpUrl()?.pathSegments?.lastOrNull()
                        val isLocked = buyHref == "buy_confirm" || buyHref == "login"
                        if (hideLocked && isLocked) return@mapNotNull null

                        SChapter.create().apply {
                            val lock = if (isLocked) "🔒 " else ""
                            setUrlWithoutDomain(href[3])
                            memo = buildJsonObject {
                                put("type", "1")
                                put("titleId", href[1])
                            }
                            val volumeName = link.text()
                            name = lock + volumeName
                            chapter_number = href[3].toFloatOrNull() ?: -1f
                        }
                    }
                }
            } else {
                break
            }

            val hasNextPage = document.selectFirst("a.next_page") != null
            if (!hasNextPage) break
            page++
        }

        return SMangaUpdate(
            manga = manga,
            chapters = if (fetchChapters) chapterList.reversed() else chapters,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.memo.getStringOrNull("free") == "1") {
        val code = chapter.memo.getString("code")
        "$baseUrl/free_chapters/${chapter.url}/download/$code"
    } else if (chapter.memo.getStringOrNull("type") == "1") {
        "$baseUrl/books/${chapter.memo.getString("titleId")}/volume/${chapter.url}/download"
    } else {
        "$baseUrl/chapters/${chapter.url}/download"
    }

    private val newClient = network.client.newBuilder()
        .followRedirects(false)
        .build()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = newClient.get(getChapterUrl(chapter), ensureSuccess = false).use { it.header("location")!!.toHttpUrl() }
        val cryptoKeyPath = url.queryParameter("manifest_url")
        val directory = url.queryParameter("directory")
        val ver = url.queryParameter("ver")
        val contentsPath = url.queryParameter("contents_vertical")
            ?: url.queryParameter("contents")
            ?: url.queryParameter("contents_page")
            ?: throw Exception("Log in via WebView and purchase this product to read.")

        val contentsUrl = contentsPath.toHttpUrl().newBuilder()
            .addQueryParameter("ver", ver)
            .build()

        val contentData = client.get(contentsUrl).parseAs<ContentData>()
        val cryptoKey = client.get("$baseUrl$cryptoKeyPath").parseAs<CryptoKey>().cryptokey
        return contentData.images.values.mapIndexed { i, pages ->
            val img = (directory + pages.first().src).toHttpUrl().newBuilder()
                .addQueryParameter("ver", ver)
                .fragment("key=$cryptoKey")
                .build()
                .toString()
            Page(i, imageUrl = img)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        Filter.Header("Novels are not supported!"),
        GenreFilter(),
        SortFilter(),
        CompletedFilter(),
    )

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
