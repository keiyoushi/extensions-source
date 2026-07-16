package eu.kanade.tachiyomi.extension.ja.mangafive

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaFive :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(PublusInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/ranking").toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseUrl/series?p=$page").toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>()
        val genre = filters.firstInstance<GenreFilter>()

        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("k", query)
                .addQueryParameter("p", page.toString())
                .addQueryParameter("order", sort.value)
                .build()
            return client.get(url).toMangasPage()
        }

        val url = "$baseUrl/genre".toHttpUrl().newBuilder()
            .addPathSegment(genre.value)
            .addQueryParameter("p", page.toString())
            .addQueryParameter("order", sort.value)
            .build()
        return client.get(url).toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        GenreFilter(),
    )

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select("a.book-list-item").map {
            SManga.create().apply {
                title = it.selectFirst("h3.title")!!.text()
                thumbnail_url = it.selectFirst("img.thum")?.absUrl("data-src")
                url = it.absUrl("href").toHttpUrl().pathSegments.last()
            }
        }
        val hasNextPage = document.selectFirst(".pagination-list-item.to-next:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val detail = document.selectFirst(".comic-main-right-detail")!!
        val titleName = detail.selectFirst("h1.comic-title")!!.text()
        val mangas = SManga.create().apply {
            title = titleName
            author = detail.select(".author-list .author").joinToString { it.text() }
            description = detail.selectFirst(".comic-discription-text")?.text()
            thumbnail_url = document.selectFirst(".comic-main-thum-wrapper img")?.absUrl("src")
            status = if ("完結" in titleName) SManga.COMPLETED else SManga.ONGOING
        }

        val chapterList = if (fetchChapters) {
            val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
            val list = mutableListOf<SChapter>()
            var doc = document
            var page = 1
            while (true) {
                list += doc.select("a.book-product-list-item").mapNotNull {
                    val locked = it.hasClass("js-read")
                    if (hideLocked && locked) return@mapNotNull null
                    SChapter.create().apply {
                        url = it.attr("data-id")
                        name = (if (locked) "🔒 " else "") + it.selectFirst("h4.title")!!.text()
                        date_upload = dateFormat.tryParse(it.selectFirst("p.update-date")?.text())
                    }
                }
                val hasNextPage = doc.selectFirst(".pagination-list-item.to-next:not(.disabled)") != null
                if (!hasNextPage) break
                page++
                doc = client.get("${getMangaUrl(manga)}?p=$page").asJsoup()
            }
            list
        } else {
            chapters
        }

        return SMangaUpdate(mangas, chapterList)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/content/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/product/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val url = response.request.url
        val cid = if (url.pathSegments.last() == "ads_before_launching_viewer.html") {
            val onclick = response.asJsoup().selectFirst("button[onclick]")!!.attr("onclick")
            onclick.substringAfter("'").substringBefore("'").toHttpUrl().queryParameter("cid")
        } else {
            response.close()
            url.queryParameter("cid")
        }
        if (cid == null) throw Exception("Log in via WebView and purchase this product to read.")

        val licenseUrl = "$baseUrl/api4js/contents/license".toHttpUrl().newBuilder()
            .addEncodedQueryParameter("cid", cid)
            .build()

        val content = client.get(licenseUrl).parseAs<PublusContent>()
        val auth = content.authInfo?.toAuth()
        val contentUrl = content.url!!

        if (content.cty == 6) {
            val packUrl = contentUrl.toHttpUrl().newBuilder()
                .addPathSegment("content.json")
                .also { auth?.applyTo(it) }
                .build()
            return client.get(packUrl).parseAs<List<List<ContentPackResponse>>>()
                .flatten()
                .flatMap { it.effectTargetImgs }
                .mapIndexed { index, path ->
                    val imageUrl = contentUrl.toHttpUrl().newBuilder()
                        .addPathSegments(path)
                        .also { auth?.applyTo(it) }
                        .build()
                    Page(index, imageUrl = imageUrl.toString())
                }
        }

        return fetchPages(contentUrl, headers, client, auth)
    }

    @Serializable
    class ContentPackResponse(
        val effectTargetImgs: List<String>,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    override val supportsRelatedMangas get() = false
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
