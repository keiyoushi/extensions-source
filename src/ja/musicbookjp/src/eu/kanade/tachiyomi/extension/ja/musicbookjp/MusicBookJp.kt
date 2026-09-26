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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
        return client.get(url).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/comic/store/newRelease".toHttpUrl().newBuilder()
            .addQueryParameter("tab", "male")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/comic/store/search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val script = document.selectFirst("script:containsData(__NUXT__)")!!.data()
        val thumbnails = THUMBNAIL_REGEX.findAll(script).joinToString(",", "[", "]") {
            """{"id":"${it.groupValues[1]}","thumbnail":${it.groupValues[2]}}"""
        }.parseAs<List<RankingThumbnail>>().associate { it.idThumbnail }

        val mangas = document.select("a.index-link:has(.ranking-title), a.index-link:has(span.title)").map {
            SManga.create().apply {
                val seriesId = it.absUrl("href").toHttpUrl().queryParameter("seriesId")!!
                url = seriesId
                title = it.selectFirst(".ranking-title .text, span.title")!!.text()
                thumbnail_url = thumbnails[seriesId]
            }
        }

        val hasNextPage = document.selectFirst("ul.pager li.pager-box.active ~ li.pager-box") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var page = 1
        var document = fetchSeriesPage(manga, page)

        val details = SManga.create().apply {
            title = document.selectFirst("article.main h1")!!.text()
            thumbnail_url = document.selectFirst("img.copy-guard-image[src*=comicsp.file]")?.absUrl("src")?.toHttpUrl()?.newBuilder()?.query(null)?.build()?.toString()
            author = document.selectFirst("article.main a[href*=authorDetails]")?.textOrNull()
            genre = document.select("section.series-additional-section a[href*=genreDetails]").joinToString { it.text() }
            status = if (document.selectFirst("article.main span.completed-flag") != null) SManga.COMPLETED else SManga.ONGOING
            description = listOfNotNull(
                document.selectFirst("section.series-outline-section p.text")?.textOrNull(),
                document.selectFirst("section.series-additional-section a[href*=publisherDetails]")?.textOrNull()?.let { "Publisher: $it" },
                document.selectFirst("section.series-additional-section a[href*=magazineDetails]")?.textOrNull()?.let { "Magazine: $it" },
            ).joinToString("\n\n")
        }

        val chapterList = mutableListOf<SChapter>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
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
            document = fetchSeriesPage(manga, ++page)
        }

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchSeriesPage(manga: SManga, page: Int): Document {
        val url = getMangaUrl(manga).toHttpUrl().newBuilder()
            .addQueryParameter("sort", "desc")
            .addQueryParameter("tab", "book")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).asJsoup()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/store/seriesDetails?seriesId=${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comic/bv/index.html?cid=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val a001 = (1..6).map { SCID_CHARS.random() }.joinToString("")
        val viewer = client.post("$baseUrl/comic/eblieva/bvApi/pageList", ViewerRequestBody(a001, chapter.url).toJsonRequestBody()).parseAs<ViewerResponse>()
        val display = viewer.data.parseAs<PageData> { it.decrypt(a001 + "bvhm" + viewer.sckb) }.displaySettingInfo.first()
        val params = display.config.paramList
        return List(params.last().lastPage) { index ->
            val coordinates = params.first { index < it.lastPage }.coordinates
            Page(index, imageUrl = "${display.layoutUrl}/${(index + 1).toString().padStart(5, '0')}.jpg#$coordinates")
        }
    }

    private fun String.decrypt(key: String): String {
        val md5Key = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(md5Key, "AES"), IvParameterSpec(IV))
        return cipher.doFinal(Base64.decode(this, Base64.DEFAULT)).decodeToString()
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
        private val THUMBNAIL_REGEX = Regex("""id:"(\d+)"[^}]*?thumbnail:("[^"]*")""")
        private const val SCID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val IV = "bvinitialvectolv".toByteArray()
    }
}
