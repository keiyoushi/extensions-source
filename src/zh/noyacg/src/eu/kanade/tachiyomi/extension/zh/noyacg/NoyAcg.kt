package eu.kanade.tachiyomi.extension.zh.noyacg

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.array
import keiyoushi.utils.getObjectOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.int
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class NoyAcg :
    KeiSource(),
    ConfigurableSource {

    private val pref by getPreferencesLazy()
    private val imgBaseUrl get() = pref.getString(IMG_HOSTING_PREF, "https://img.noymanga.com")!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context).forEach(screen::addPreference)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "NoyAcg/3.0")
        add("allow-adult", pref.getString(ADULT_PREF, "both")!!)
    }

    private fun Response.parseManga(): MangaDetailDto {
        val jsonObject = parseAs<JsonObject>()
        val status = jsonObject.getStringOrNull("status") ?: "error"
        val book = jsonObject.getObjectOrNull("book")
        val info = book?.get("info")?.parseAs<MangaDto>()
        val recommend = book?.get("recommend")?.parseAs<List<RecommendMangaDto>>()
        val categories = jsonObject["chapters"]?.obj?.get("categories")?.array?.map { it.parseAs<CategoryDto>() } ?: emptyList()
        val chaptersMap = mutableMapOf<Int, List<ChapterDto>>()
        jsonObject["chapters"]?.obj?.get("data")?.obj?.forEach { (key, value) ->
            key.toIntOrNull()?.let { categoryId -> chaptersMap[categoryId] = value.array.map { it.parseAs<ChapterDto>() } }
        }
        val chapters = categories.associate { category -> category.name to (chaptersMap[category.id] ?: emptyList()) }
        return MangaDetailDto(status, info, recommend, chapters)
    }

    private fun mangaPageParse(response: Response, page: Int): MangasPage {
        val result = response.parseAs<ListingPageDto>()
        if (result.status != "ok") throw Exception("請在 WebView 中登入")
        val mangas = result.info!!.map { it.toSManga(imgBaseUrl) }
        return MangasPage(mangas, page * LISTING_PAGE_SIZE < result.len!!)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val type = pref.getString(POPULAR_MANGAS_PREF, "day")!!
        val body = FormBody.Builder().addEncoded("type", type).addEncoded("page", page.toString())
        val response = client.post("$baseUrl/api/readLeaderboard", body.build())
        return mangaPageParse(response, page)
    }

    // Updates

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val body = FormBody.Builder().addEncoded("page", page.toString()).addEncoded("sort", "new")
        val response = client.post("$baseUrl/api/b1/booklist", body.build())
        return mangaPageParse(response, page)
    }

    // Search

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = FormBody.Builder().addEncoded("value", query).addEncoded("page", page.toString()).addEncoded("type", "book")
        filters.filterIsInstance<SearchFilter>().forEach { it.addTo(body) }
        val response = client.post("$baseUrl/api/v4/search/fetch", body.build())
        val result = response.parseAs<SearchPageDto>()
        if (result.status != "ok") throw Exception("請在 WebView 中登入")
        val mangas = result.data!!.map(SearchMangaDto::toSManga)
        return MangasPage(mangas, page * LISTING_PAGE_SIZE < result.count!!)
    }

    // Manga & Chapters

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/reader/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl) = url.pathSegments.last().toIntOrNull()?.let {
        client.get("$baseUrl/api/v4/book/$it?comment=false").parseManga().book!!.toSManga(imgBaseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl/api/v4/book/${manga.url}?comment=false")
        val comic = response.parseManga()
        if (comic.status != "ok") throw Exception("請在 WebView 中登入")

        val sManga = comic.book!!.toSManga(imgBaseUrl)

        val mangaId = response.request.url.pathSegments.last()
        val sChapters = if (comic.chapters!!.isEmpty()) {
            listOf(
                SChapter.create().apply {
                    url = mangaId
                    name = "單章節（${comic.book.len}P）"
                    date_upload = comic.book.time * 1000
                    chapter_number = 0F
                    memo = buildJsonObject { put("size", comic.book.len) }
                },
            )
        } else {
            comic.chapters.flatMap { category ->
                category.value.map {
                    SChapter.create().apply {
                        url = "$mangaId/${it.id}"
                        name = "${it.name}（${it.count}P）"
                        date_upload = it.createdAt * 1000
                        // chapter_number = it.sort.toFloat()
                        scanlator = category.key
                    }
                }.reversed()
            }
        }

        return SMangaUpdate(sManga, sChapters)
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val comic = client.get("$baseUrl/api/v4/book/${manga.url}?comment=false").parseManga()
        return comic.recommend?.map(RecommendMangaDto::toSManga) ?: emptyList()
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter) = List(chapter.memo["size"]!!.int) {
        Page(it, imageUrl = "/${chapter.url}/${it + 1}.webp")
    }

    override fun imageRequest(page: Page): Request {
        val imgBaseUrl = pref.getString(IMG_HOSTING_PREF, "https://img.noymanga.com")!!
        return GET(imgBaseUrl + page.imageUrl, headers)
    }
}
