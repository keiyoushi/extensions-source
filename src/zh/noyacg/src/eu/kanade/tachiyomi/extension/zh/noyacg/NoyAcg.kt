package eu.kanade.tachiyomi.extension.zh.noyacg

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class NoyAcg :
    HttpSource(),
    ConfigurableSource {

    override val name = "NoyAcg"
    override val lang = "zh"
    override val supportsLatest = true
    override val baseUrl = "https://beta.noyteam.online"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context).forEach(screen::addPreference)
    }

    private val pref by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder().add("referer", "$baseUrl/")
        .add("allow-adult", pref.getString(ADULT_PREF, "both")!!)

    private val json: Json by injectLazy()

    private fun Response.parseMangaDetail(): MangaDetailDto = try {
        body.byteStream().use { inputStream ->
            val jsonObject = json.decodeFromStream<JsonElement>(inputStream).jsonObject

            val status = jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "error"
            val book = jsonObject["book"]?.jsonObject?.get("info")?.let {
                json.decodeFromJsonElement<MangaDto>(it)
            }
            val categories = jsonObject["chapters"]?.jsonObject?.get("categories")?.jsonArray?.map {
                json.decodeFromJsonElement<CategoryDto>(it)
            } ?: emptyList()
            val chaptersMap = mutableMapOf<Int, List<ChapterDto>>()
            jsonObject["chapters"]?.jsonObject?.get("data")?.jsonObject?.forEach { (key, value) ->
                val categoryId = key.toIntOrNull()
                if (categoryId != null) {
                    val chapterList = value.jsonArray.map { item ->
                        json.decodeFromJsonElement<ChapterDto>(item)
                    }
                    chaptersMap[categoryId] = chapterList
                }
            }
            val chapters = categories.map { category ->
                category.name to (chaptersMap[category.id] ?: emptyList())
            }
            MangaDetailDto(status, book, chapters)
        }
    } catch (_: Exception) {
        throw Exception("请在 WebView 中登录")
    } finally {
        close()
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val body = FormBody.Builder().addEncoded("type", "day").addEncoded("page", page.toString())
        return POST("$baseUrl/api/readLeaderboard#$page", headers, body.build())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ListingPageDto>()
        check(result.status == "ok") { throw Exception("请在 WebView 中登录") }
        val page = response.request.url.fragment!!.toInt()
        val mangas = result.info!!.map(MangaDto::toSManga)
        return MangasPage(mangas, page * LISTING_PAGE_SIZE < result.len!!)
    }

    // Latest Updates

    override fun latestUpdatesRequest(page: Int): Request {
        val body = FormBody.Builder().addEncoded("page", page.toString()).addEncoded("sort", "new")
        return POST("$baseUrl/api/b1/booklist#$page", headers, body.build())
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search

    override fun getFilterList() = getFilterListInternal()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = FormBody.Builder().addEncoded("value", query)
            .addEncoded("page", page.toString())
            .addEncoded("type", "book")
        filters.filterIsInstance<SearchFilter>().forEach { it.addTo(body) }
        return POST("$baseUrl/api/v4/search/fetch#$page", headers, body.build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchPageDto>()
        check(result.status == "ok") { throw Exception("请在 WebView 中登录") }
        val page = response.request.url.fragment!!.toInt()
        val mangas = result.data!!.map(SearchMangaDto::toSManga)
        return MangasPage(mangas, page * LISTING_PAGE_SIZE < result.count!!)
    }

    // Manga

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/api/v4/book/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.parseMangaDetail()
        check(manga.status == "ok") { throw Exception("请在 WebView 中登录") }
        return SManga.create().apply {
            url = manga.book!!.id.toString()
            title = manga.book.name
            author = manga.book.author
            description = manga.book.formatDescription()
            genre = manga.book.tags.replace(" ", ", ")
            status = if (manga.book.mode == 0 || manga.book.status == 1) SManga.COMPLETED else SManga.ONGOING
        }
    }

    // Chapter

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/reader/${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseMangaDetail()
        check(manga.status == "ok") { throw Exception("请在 WebView 中登录") }
        val mangaId = response.request.url.pathSegments.last()
        return if (manga.chapters!!.isEmpty()) {
            listOf(
                SChapter.create().apply {
                    url = "$mangaId/0"
                    name = "单章节"
                    date_upload = manga.book!!.time * 1000
                    chapter_number = 0F
                    scanlator = "${manga.book.len}P"
                },
            )
        } else {
            manga.chapters.flatMap { category ->
                category.second.map {
                    SChapter.create().apply {
                        url = "$mangaId/${it.id}"
                        name = it.name
                        date_upload = it.createdAt * 1000
                        // chapter_number = it.sort.toFloat()
                        scanlator = "${category.first} • ${it.count}P"
                    }
                }.reversed()
            }.reversed()
        }
    }

    // Pages

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val size = chapter.scanlator!!.substringAfter(" • ").substringBefore('P').toInt()
        return Observable.just(
            List(size) { Page(it, imageUrl = "https://img.noymanga.com/${chapter.url}/${it + 1}.webp") },
        )
    }

    // Image

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
