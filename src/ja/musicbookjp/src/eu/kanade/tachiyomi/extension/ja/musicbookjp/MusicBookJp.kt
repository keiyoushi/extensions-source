package eu.kanade.tachiyomi.extension.ja.musicbookjp

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.md5
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

@Source
abstract class MusicBookJp :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun getHomeUrl(): String = "$baseUrl/comic"

    // mobile ua gives infinite redirects
    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/1540.0.0 Safari/537.36")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor { client })
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.request.url.pathSegments.last() == "oversea.html") {
                throw IOException("This service is only available in Japan.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/comic/store/rankingDetails".toHttpUrl().newBuilder()
            .addQueryParameter("rankingType", "D")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, Fetch.DOCUMENT).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/comic/store/newRelease".toHttpUrl().newBuilder()
            .addQueryParameter("tab", "male")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, Fetch.DOCUMENT).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/comic/store/search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, Fetch.DOCUMENT).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = asJsoup()
        val mangas = document.select("div.index-link").map {
            val link = it.selectFirst("a.stretched-link[href*=seriesId]")!!
            SManga.create().apply {
                url = link.absUrl("href").toHttpUrl().queryParameter("seriesId")!!
                title = link.attrOrNull("aria-label")!!
                thumbnail_url = it.selectFirst("img.copy-guard-image")?.coverUrl()
            }
        }

        val hasNextPage = document.selectFirst("ul.pager li.pager-box.active ~ li.pager-box") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.encodedPath != "/comic/store/seriesDetails") return null
        val seriesId = url.queryParameter("seriesId")?.takeIf { it.isNotEmpty() } ?: return null
        return fetchSeriesPage(seriesId, 1).toSManga().apply {
            this.url = seriesId
            initialized = true
        }
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst("article.main h1")!!.text()
        thumbnail_url = selectFirst("img.copy-guard-image[src*=comicsp.file]")?.coverUrl()
        author = selectFirst("article.main a[href*=authorDetails]")?.textOrNull()
        genre = select("section.series-additional-section a[href*=genreDetails]").joinToString { it.text() }.ifEmpty { null }
        status = if (selectFirst("article.main span.completed-flag") != null) SManga.COMPLETED else SManga.ONGOING
        description = MarkdownDescription.Builder()
            .description(selectFirst("section.series-outline-section p.text"))
            .field("Publisher", selectFirst("section.series-additional-section a[href*=publisherDetails]")?.textOrNull())
            .field("Magazine", selectFirst("section.series-additional-section a[href*=magazineDetails]")?.textOrNull())
            .build()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var page = 1
        var document = fetchSeriesPage(manga.url, page)
        val details = document.toSManga()

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val chapterList = mutableListOf<SChapter>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, true)
        while (true) {
            chapterList += document.select("ul.item-list li.item").mapNotNull {
                val readContents = it.selectFirst("a[href*=readContents]")
                val locked = readContents == null
                if (locked && hideLocked) return@mapNotNull null

                SChapter.create().apply {
                    val lock = if (locked) "🔒 " else ""
                    this.url = readContents?.absUrl("href")?.toHttpUrl()?.queryParameter("itemId") ?: it.selectFirst("img")!!.absUrl("src").toHttpUrl().pathSegments[1]
                    name = lock + it.selectFirst("span.title")!!.text()
                }
            }

            val hasNextPage = document.selectFirst("ul.pager li.pager-box.active ~ li.pager-box") != null
            if (!hasNextPage) break
            document = fetchSeriesPage(manga.url, ++page)
        }

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchSeriesPage(seriesId: String, page: Int): Document {
        val url = seriesUrl(seriesId)
            .addQueryParameter("sort", "desc")
            .addQueryParameter("tab", "book")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
    }

    private fun seriesUrl(seriesId: String) = "$baseUrl/comic/store/seriesDetails".toHttpUrl().newBuilder()
        .addQueryParameter("seriesId", seriesId)

    override fun getMangaUrl(manga: SManga): String = seriesUrl(manga.url).build().toString()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comic/bv/index.html?cid=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val a001 = (1..6).map { SCID_CHARS.random() }.joinToString("")
        val viewer = client.post("$baseUrl/comic/eblieva/bvApi/pageList", Fetch.XHR, ViewerRequestBody(a001, chapter.url).toJsonRequestBody()).parseAs<ViewerResponse>()
        val display = viewer.data.parseAs<PageData> { it.decrypt(a001 + "bvhm" + viewer.sckb) }.displaySettingInfo.first()
        val params = display.config.paramList
        return List(params.last().lastPage) { index ->
            val coordinates = params.first { index < it.lastPage }.coordinates
            Page(index, imageUrl = "${display.layoutUrl}/${(index + 1).toString().padStart(5, '0')}.jpg#$coordinates")
        }
    }

    private fun String.decrypt(key: String): String {
        val md5Key = key.md5()
        return Base64.decode(this, Base64.DEFAULT).aesDecrypt(aesCbc(md5Key, IV)).decodeToString()
    }

    private fun Element.coverUrl() = absUrl("src").toHttpUrlOrNull()?.newBuilder()?.query(null)?.build()?.toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val SCID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val IV = "bvinitialvectolv".encodeToByteArray()
    }
}
