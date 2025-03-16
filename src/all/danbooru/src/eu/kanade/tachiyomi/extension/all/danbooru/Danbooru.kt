package eu.kanade.tachiyomi.extension.all.danbooru

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Danbooru : HttpSource(), ConfigurableSource {
    override val name: String = "Danbooru"
    override val baseUrl: String = "https://danbooru.donmai.us"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

    override val client = network.cloudflareClient

    private val dateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)

    private val preference by getPreferencesLazy()

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response) =
        searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

                else -> throw IllegalStateException("Unrecognized filter")
            }
        }

        url.addEncodedQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search[name_contains]", query)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

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

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(FilterOrder("created_at")))

    override fun latestUpdatesParse(response: Response): MangasPage =
        searchMangaParse(response)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()

        setUrlWithoutDomain(document.location())
        title = document.selectFirst(".pool-category-series, .pool-category-collection")!!.text()
        description = document.getElementById("description")?.wholeText()
        author = document.selectFirst("#description a[href*=artists]")?.ownText()
        artist = author
        update_strategy = if (!preference.splitChaptersPref) {
            UpdateStrategy.ONLY_FETCH_ONCE
        } else {
            UpdateStrategy.ALWAYS_UPDATE
        }
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}.json", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Pool>()

        return if (preference.splitChaptersPref) {
            data.postIds.mapIndexed { index, id ->
                SChapter.create().apply {
                    url = "/posts/$id"
                    name = "Post ${index + 1}"
                    chapter_number = index + 1f
                }
            }.reversed().apply {
                if (isNotEmpty()) {
                    this[0].date_upload = dateFormat.tryParse(data.updatedAt)
                }
            }
        } else {
            listOf(
                SChapter.create().apply {
                    url = "/pools/${data.id}"
                    name = "Oneshot"
                    date_upload = dateFormat.tryParse(data.updatedAt)
                    chapter_number = 0F
                },
            )
        }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl${chapter.url}.json", headers)

    override fun pageListParse(response: Response): List<Page> =
        if (response.request.url.toString().contains("/posts/")) {
            val data = response.parseAs<Post>()

            listOf(
                Page(index = 0, imageUrl = data.fileUrl),
            )
        } else {
            val data = response.parseAs<Pool>()

            data.postIds.mapIndexed { index, id ->
                Page(index, url = "/posts/$id")
            }
        }

    override fun imageUrlRequest(page: Page): Request =
        GET("$baseUrl${page.url}.json", headers)

    override fun imageUrlParse(response: Response): String =
        response.parseAs<Post>().fileUrl

    override fun getChapterUrl(chapter: SChapter): String =
        baseUrl + chapter.url

    override fun getFilterList() = FilterList(
        listOf(
            FilterDescription(),
            FilterTags(),
            FilterIsDeleted(),
            FilterCategory(),
            FilterOrder(),
        ),
    )

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
