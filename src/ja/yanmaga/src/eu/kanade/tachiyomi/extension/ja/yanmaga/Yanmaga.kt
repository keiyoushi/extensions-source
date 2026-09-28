package eu.kanade.tachiyomi.extension.ja.yanmaga

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
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Yanmaga :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val isGravure get() = name.contains("グラビア")
    private val workPath get() = if (isGravure) "gravures/books" else "comics"
    private val latestPath get() = if (isGravure) workPath else "comics/series"
    private val workNameIndex get() = if (isGravure) 2 else 1
    private val SManga.isGravureBook get() = url.startsWith("/gravures/books/")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.of("Asia/Tokyo"))
    private val xhrHeaders get() = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    override fun getHomeUrl(): String = if (isGravure) "$baseUrl/gravures" else super.getHomeUrl()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(SpeedBinbInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (isGravure) {
            val document = client.get("$baseUrl/gravures/series?page=$page").asJsoup()
            val mangas = document.select("a.banner-link").map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = it.selectFirst(".text-wrapper h2")!!.text()
                    thumbnail_url = it.selectFirst(".img-bg-wrapper")?.absUrl("data-bg")?.toThumbnail()
                }
            }
            val hasNextPage = document.selectFirst("ul.pagination > li.page-item > a.page-next") != null
            return MangasPage(mangas, hasNextPage)
        }

        val mangas = client.get("$baseUrl/ranking?ranking-index=0").asJsoup()
            .selectFirst("[data-tab-name]")!!
            .select("a.mod-ranking-v2-link")
            .map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = it.selectFirst(".mod-ranking-v2-title")!!.text()
                    thumbnail_url = it.selectFirst("img")?.absUrl("data-src")?.toThumbnail()
                }
            }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/$latestPath/newer/more".toHttpUrl().newBuilder()
            .addQueryParameter("offset", ((page - 1) * LATEST_UPDATES_PER_PAGE).toString())
            .build()

        val mangas = client.get(url, xhrHeaders).parseInsertAdjacentHtml().select("a.banner-link").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".text-wrapper h2")!!.text()
                thumbnail_url = it.selectFirst(".img-bg-wrapper")?.absUrl("data-bg")?.toThumbnail()
                if (isGravureBook) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
        val hasNextPage = mangas.size == LATEST_UPDATES_PER_PAGE
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)

            if (isGravure) {
                addQueryParameter("tab", "gravures")
                addQueryParameter("sub", "book")
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        val response = client.get(url, ensureSuccess = false)
        // No results are answered with a 404 error
        if (response.code == 404) {
            response.close()
            return MangasPage(emptyList(), false)
        }

        val document = response.asJsoup()
        val mangas = document.select("main a[href^=/$workPath/]:has(img)").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst("p")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")?.toThumbnail()
                if (isGravureBook) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }

        val hasNextPage = document.selectFirst("main a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        if (manga.isGravureBook) {
            val segments = getMangaUrl(manga).toHttpUrl().pathSegments
            val chapter = SChapter.create().apply {
                url = segments[3]
                name = "作品"
                memo = buildJsonObject {
                    put("name", segments[2])
                }
            }
            return@coroutineScope SMangaUpdate(manga, listOf(chapter))
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga)).asJsoup()
            SManga.create().apply {
                if (isGravure) {
                    title = document.selectFirst(".detail-header-title")!!.text()
                    genre = document.select(".ga-tag").joinToString { it.text() }
                    thumbnail_url = document.selectFirst(".detail-header-image img")?.absUrl("src")?.toThumbnail()
                } else {
                    title = document.selectFirst(".detailv2-outline-title")!!.text()
                    author = document.select(".detailv2-outline-author-item a").joinToString { it.text() }
                    description = document.selectFirst(".detailv2-description")?.textOrNull()
                    genre = document.select(".detailv2-tag .ga-tag").joinToString { it.text() }
                    thumbnail_url = document.selectFirst(".detailv2-thumbnail-image img")?.absUrl("src")?.toThumbnail()
                    status = if (document.selectFirst(".detailv2-link-note") != null) {
                        SManga.ONGOING
                    } else {
                        SManga.COMPLETED
                    }
                }
            }
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val episodesUrl = "${getMangaUrl(manga)}/${if (isGravure) "more" else "episodes"}".toHttpUrl()
            var offset = 0
            buildList {
                do {
                    val pageUrl = episodesUrl.newBuilder()
                        .addQueryParameter("offset", offset.toString())
                        .build()

                    val episodes = client.get(pageUrl, xhrHeaders).parseInsertAdjacentHtml().select("li.mod-episode-item")
                    episodes.mapNotNullTo(this) {
                        val link = it.selectFirst("a.mod-episode-link") ?: return@mapNotNullTo null
                        val isLocked = it.hasClass("js-modal") || it.selectFirst(".mod-episode-price:not(:has(.mod-episode-point--free))") != null

                        if (hideLocked && isLocked) return@mapNotNullTo null

                        SChapter.create().apply {
                            val segments = link.absUrl("href").toHttpUrl().pathSegments
                            val title = it.selectFirst(".mod-episode-title")!!.text()
                            url = segments[workNameIndex + 1]
                            name = if (isLocked) "🔒 $title" else title
                            date_upload = dateFormat.tryParseDate(it.selectFirst(".mod-episode-date")?.textOrNull())
                            memo = buildJsonObject {
                                put("name", segments[workNameIndex])
                            }
                        }
                    }
                    offset += episodes.size
                } while (episodes.size == 150)
            }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/$workPath/${chapter.memo["name"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        if (document.selectFirst(".ga-rental-modal-sign-up") != null) {
            throw Exception("このストーリーを読むには WebView でログイン")
        }

        if (document.selectFirst(".ga-modal-open") != null) {
            throw Exception("WebView でポイントを使用してこのストーリーをレンタル")
        }

        return client.fetchPages(document)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val LATEST_UPDATES_PER_PAGE = 12
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
