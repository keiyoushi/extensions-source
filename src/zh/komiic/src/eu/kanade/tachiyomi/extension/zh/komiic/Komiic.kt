package eu.kanade.tachiyomi.extension.zh.komiic

import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.io.IOException

@Source
abstract class Komiic :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val origin = chain.request()
            val host = baseUrl.removePrefix("https://")
            val request = origin.takeUnless { host != "komiic.com" && it.url.host.endsWith("komiic.com") } ?: origin.run {
                val newHost = url.host.removeSuffix("komiic.com") + host
                newBuilder().url(url.newBuilder().host(newHost).build()).build()
            }
            chain.proceed(request)
        }
        addInterceptor { chain ->
            val origin = chain.request()
            if (origin.url.toString().contains("api/image")) {
                refreshToken(chain)
                chain.proceed(origin).also {
                    if (it.code == 402) {
                        it.close()
                        throw IOException("今日圖片讀取次數已達上限，請登录或明天再來！")
                    }
                }
            } else {
                chain.proceed(origin)
            }
        }
    }

    private fun refreshToken(chain: Interceptor.Chain) {
        client.cookieJar.loadForRequest(chain.request().url).find { it.name == "komiic-access-token" }?.let {
            val payload = Base64.decode(it.value.split(".")[1], Base64.DEFAULT).decodeToString()
            if (System.currentTimeMillis() + 3600_000 >= payload.parseAs<JwtPayload>().exp * 1000) {
                val response = chain.proceed(POST("$baseUrl/auth/refresh", headers)).apply { close() }
                if (!response.isSuccessful) throw IOException("刷新 Token 失敗：HTTP ${response.code}")
            }
        }
    }

    private val pref by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "CHAPTER_FILTER"
            title = "章節列表顯示"
            summary = "%s"
            entries = arrayOf("同時顯示卷和章節", "僅顯示章節", "僅顯示卷")
            entryValues = arrayOf("all", "chapter", "book")
            setDefaultValue("all")
        }.also(screen::addPreference)
    }

    // Customize

    private val SManga.id get() = url.substringAfterLast("/")
    private val SChapter.id get() = url.substringAfterLast("/")

    private suspend fun OkHttpClient.query(body: RequestBody) = post("$baseUrl/api/query", body)

    private suspend fun mangasPage(page: Int, orderBy: OrderBy): MangasPage {
        val pagination = Pagination((page - 1) * PAGE_SIZE, orderBy)
        val response = client.query(commonQuery(ListingVariables(pagination)))
        return parseListing(response.parseGraphQLAs())
    }

    // Popular
    override suspend fun getPopularManga(page: Int) = mangasPage(page, OrderBy.MONTH_VIEWS)

    // Update
    override suspend fun getLatestUpdates(page: Int) = mangasPage(page, OrderBy.DATE_UPDATED)

    // Search
    override fun getFilterList(data: JsonElement?) = buildFilterList()

    override suspend fun getMangaByUrl(url: HttpUrl) = url.takeIf { url.pathSegments[0] == "comic" }?.let {
        val response = client.query(idsQuery(listOf(url.pathSegments[1])))
        parseListing(response.parseGraphQLAs()).mangas.first()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = if (query.isNotBlank()) {
            searchQuery(query)
        } else {
            val variables = ListingVariables(Pagination((page - 1) * PAGE_SIZE))
            filters.filterIsInstance<KomiicFilter>().forEach { it.apply(variables) }
            listingQuery(variables)
        }
        val response = client.query(body)
        return parseListing(response.parseGraphQLAs())
    }

    // Manga & Chapter
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url + "/images/all"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.query(mangaQuery(manga.id, fetchDetails, fetchChapters))
        val data = response.parseGraphQLAs<DataDto>()

        val sManga = if (fetchDetails) data.comicById!!.toSManga() else manga
        val sChapters = if (fetchChapters) {
            val rawChapters = data.chaptersByComicId!!.toMutableList()
            when (pref.getString("CHAPTER_FILTER", "all")) {
                "chapter" -> rawChapters.retainAll { it.type == "chapter" }
                "book" -> rawChapters.retainAll { it.type == "book" }
                else -> {}
            }
            rawChapters.sortWith(
                compareByDescending<ChapterDto> { it.type }.thenByDescending { it.serial.toFloatOrNull() },
            )
            rawChapters.map { it.toSChapter(manga.url) }
        } else {
            chapters
        }

        return SMangaUpdate(sManga, sChapters)
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val response = client.query(recommendQuery(manga.id))
        val comicIds = response.parseGraphQLAs<DataDto>().recommendComicById!!
        return parseListing(client.query(idsQuery(comicIds)).parseGraphQLAs()).mangas
    }

    // Page
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.query(pageListQuery(chapter.id))
        val data = response.parseGraphQLAs<DataDto>()
        return data.imagesByChapterId!!.mapIndexed { index, image ->
            Page(index, baseUrl + "${chapter.url}/page/${index + 1}", "$baseUrl/api/image/${image.kid}")
        }
    }

    // Image
    override fun imageRequest(page: Page) = super.imageRequest(page).newBuilder()
        .addHeader("Accept", "*/*").header("Referer", page.url).build()
}
