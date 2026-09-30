package eu.kanade.tachiyomi.extension.en.mangaplaza

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
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.fetchPages
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup

@Source
abstract class MangaPlaza :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/api"
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(SpeedBinbInterceptor())
        addCookie("mp_over18_agreement" to "ON")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 2 }))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/searchresult".toHttpUrl().newBuilder().apply {
            addPathFilter("genre", filters.firstInstanceOrNull<GenreFilter>())
            addPathFilter("genre_tag", filters.firstInstanceOrNull<TagFilter>())
            addQueryParameter("fre", query)
            addQueryParameter("sort", filters.firstInstance<SortFilter>().value)
            addQueryParameter("page", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select("ul.listBox > li").map {
            SManga.create().apply {
                val link = it.selectFirst(".titleName a")!!
                url = link.absUrl("href").toHttpUrl().pathSegments[1]
                title = link.text()
                thumbnail_url = it.selectFirst("figure img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".pager li.selected + li a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga)).asJsoup()
            SManga.create().apply {
                title = document.selectFirst(".detailBlock h1")!!.text()
                author = document.select(".detailBlock .authorName a").joinToString { it.text() }
                description = document.selectFirst(".titleInfo .storytext")?.textOrNull()
                genre = document.select(".titleInfo .infoList a[href*=genre]").joinToString { it.text() }
                val label = document.selectFirst(".detailTopBlock .number")?.textOrNull()?.lowercase().orEmpty()
                status = when {
                    label.startsWith("ongoing") -> SManga.ONGOING
                    label.startsWith("complete") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                thumbnail_url = document.selectFirst(".detailBlock .thumBlock img")?.absUrl("src")
            }
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val listUrl = "$apiUrl/title/content_list".toHttpUrl().newBuilder()
                .addQueryParameter("title_id", manga.url)
                .addQueryParameter("order", "down")

            val firstPage = client.get(listUrl.setQueryParameter("page", "1").build()).parseAs<ApiResponse<ContentList>>().data
            val lastPage = Jsoup.parseBodyFragment(firstPage.htmlPage).select("a[data-page]").maxOfOrNull { it.attr("data-page").toInt() } ?: 1
            val otherPages = (2..lastPage).map { page ->
                val pageUrl = listUrl.setQueryParameter("page", page.toString()).build()
                async { client.get(pageUrl).parseAs<ApiResponse<ContentList>>().data }
            }

            (listOf(firstPage) + otherPages.awaitAll()).flatMap { page ->
                Jsoup.parseBodyFragment(page.htmlContent).select("ul.detailBox > li:not(:has(.nextUpdateBlock))").mapNotNull {
                    val isLocked = it.selectFirst(".btnBox a[href*=/reader/]:not([href*=/preview/])") == null
                    if (hideLocked && isLocked) return@mapNotNull null
                    val isPreview = isLocked && it.selectFirst(".btnBox a[href*=/preview/]") != null
                    SChapter.create().apply {
                        url = it.id().removePrefix("_content_area_")
                        name = when {
                            !isLocked -> ""
                            isPreview -> "🔒 (Preview) "
                            else -> "🔒 "
                        } + it.selectFirst(".titleName")!!.text()
                        memo = buildJsonObject {
                            put("preview", isPreview)
                        }
                    }
                }
            }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}" + if (chapter.memo["preview"]!!.boolean) "/preview" else ""

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "https://reader.mangaplaza.com/sws/apis/bibGetCntntInfo.php".toHttpUrl().newBuilder()
            .addQueryParameter("u0", if (chapter.memo["preview"]!!.boolean) "1" else "0")
            .addQueryParameter("u1", baseUrl)
            .build()
        return client.fetchPages(url, chapter.url).ifEmpty { throw Exception("Log in via WebView and purchase this chapter to read.") }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = client.get("$apiUrl/genre/all_genre").parseAs<ApiResponse<GenreList>>().data
        return GenreList(genres.genres, genres.tags.distinctBy { it.id }.sortedBy { it.name }).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<GenreList>() ?: return FilterList(SortFilter())
        return FilterList(
            SortFilter(),
            GenreFilter(genres.genres.map { it.name to it.id }),
            TagFilter(genres.tags.map { it.name to it.id }),
        )
    }

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
