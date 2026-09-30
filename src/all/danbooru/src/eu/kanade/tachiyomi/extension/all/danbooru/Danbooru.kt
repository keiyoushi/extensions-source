package eu.kanade.tachiyomi.extension.all.danbooru

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class Danbooru :
    KeiSource(),
    ConfigurableSource {

    // Make image requests mimic a standard browser <img> fetch to bypass CF 403s on the CDN
    private val cdnInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host == "cdn.donmai.us") {
            val newRequest = request.newBuilder()
                .removeHeader("Cookie") // CF flags CDN requests containing main-domain session cookies
                .header("Accept", "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
                .header("Sec-Fetch-Dest", "image")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Site", "same-site")
                .build()
            return@Interceptor chain.proceed(newRequest)
        }
        chain.proceed(request)
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(cdnInterceptor)
        .rateLimit(2)

    private val preference by getPreferencesLazy()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(filterOrder("created_at")))

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/pools/gallery".toHttpUrl().newBuilder()

        url.setEncodedQueryParameter("search[category]", "series")

        filters.forEach {
            when (it) {
                is FilterTags -> if (it.state.isNotBlank()) {
                    url.addQueryParameter("search[post_tags_match]", it.state)
                }
                is FilterDescription -> if (it.state.isNotBlank()) {
                    url.addQueryParameter("search[description_matches]", it.state)
                }
                is FilterIsDeleted -> if (it.state) {
                    url.addEncodedQueryParameter("search[is_deleted]", "true")
                }
                is FilterCategory -> {
                    url.setEncodedQueryParameter("search[category]", it.selected)
                }
                is FilterOrder -> if (it.selected != null) {
                    url.addEncodedQueryParameter("search[order]", it.selected)
                }
                else -> {}
            }
        }

        url.addEncodedQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search[name_contains]", query)
        }

        val document = client.get(url.build()).asJsoup()

        val entries = document.select("article.post-preview").map {
            searchMangaFromElement(it)
        }
        val hasNextPage = document.selectFirst("a.paginator-next") != null

        return MangasPage(entries, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        url = element.selectFirst(".post-preview-link")!!.attr("href")
        title = element.selectFirst("div.text-center")!!.text()

        thumbnail_url = element.selectFirst("source")?.attr("srcset")
            ?.substringAfterLast(',')?.trim()
            ?.substringBeforeLast(' ')?.trimStart()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val path = url.pathSegments
        if (path.size < 2 || path[0] != "pools") return null

        return mangaDetails(
            SManga.create().apply {
                this.url = "/pools/${path[1]}"
            },
        )
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) mangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) chapterList(manga) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun mangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return manga.apply {
            title = document.selectFirst(".pool-category-series, .pool-category-collection")?.text()
                ?: document.selectFirst("h1")!!.text()
            description = document.getElementById("description")?.wholeText()
            author = document.selectFirst("#description a[href*=artists]")?.ownText()
            artist = author
            update_strategy = if (!preference.splitChaptersPref) {
                UpdateStrategy.ONLY_FETCH_ONCE
            } else {
                UpdateStrategy.ALWAYS_UPDATE
            }
        }
    }

    // ============================= Chapters ==============================

    private suspend fun chapterList(manga: SManga): List<SChapter> {
        val data = client.get("$baseUrl${manga.url}.json").parseAs<Pool>()

        return if (preference.splitChaptersPref) {
            data.postIds.mapIndexed { index, id ->
                SChapter.create().apply {
                    url = "/posts/$id"
                    name = "Post ${index + 1}"
                    chapter_number = index + 1f
                }
            }.reversed().apply {
                if (isNotEmpty()) {
                    this[0].date_upload = Instant.tryParse(data.updatedAt)
                }
            }
        } else {
            listOf(
                SChapter.create().apply {
                    url = "/pools/${data.id}"
                    name = "Oneshot"
                    date_upload = Instant.tryParse(data.updatedAt)
                    chapter_number = 0F
                },
            )
        }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl${chapter.url}.json"

        return if (chapter.url.contains("/posts/")) {
            listOf(
                Page(index = 0, imageUrl = fetchImageUrl(url)),
            )
        } else {
            val data = client.get(url).parseAs<Pool>()

            data.postIds.mapIndexed { index, id ->
                Page(index, url = "/posts/$id")
            }
        }
    }

    override suspend fun getImageUrl(page: Page): String = fetchImageUrl("$baseUrl${page.url}.json")

    private suspend fun fetchImageUrl(url: String): String {
        val imageUrl = client.get(url).parseAs<Post>().bestUrl
        return if (imageUrl.startsWith("http")) imageUrl else "$baseUrl$imageUrl"
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        FilterDescription(),
        FilterTags(),
        FilterIsDeleted(),
        FilterCategory(),
        FilterOrder(),
    )

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = CHAPTER_LIST_PREF
            title = "Split posts into individual chapters"
            summary = """
                Instead of showing one 'OneShot' chapter,
                each post will be it's own chapter
            """.trimIndent()
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.splitChaptersPref: Boolean
        get() = getBoolean(CHAPTER_LIST_PREF, false)
}

private const val CHAPTER_LIST_PREF = "prefChapterList"
