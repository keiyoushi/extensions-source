package eu.kanade.tachiyomi.extension.en.likemanga

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LikeManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds) { it.queryParameter("code") == "ajax" && it.queryParameter("code") == "load_list_chapter" }
        rateLimit(1) { it.host == baseUrl.toHttpUrl().host && it.fragment != THUMBNAIL }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("top-manga")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("lastest-chap")))

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }

        return fetchMangaUpdate(
            manga = SManga.create().apply {
                setUrlWithoutDomain(url.toString())
            },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("act", "searchadvance")
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.checked?.forEach {
                            addQueryParameter("f[genres][]", it)
                        }
                    }
                    is ChapterCountFilter -> {
                        filter.selected?.let {
                            addQueryParameter("f[min_num_chapter]", it)
                        }
                    }
                    is StatusFilter -> {
                        filter.selected?.let {
                            addQueryParameter("f[status]", it)
                        }
                    }
                    is SortFilter -> {
                        filter.selected?.let {
                            addQueryParameter("f[sortby]", it)
                        }
                    }
                    else -> {}
                }
            }
            if (query.isNotEmpty()) {
                addQueryParameter("f[keyword]", query.trim())
            }
            if (page > 1) {
                addQueryParameter("pageNum", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()

        val mangas = document.select("div.card-body div.card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                thumbnail_url = element.selectFirst("img")?.imgAttr(thumbnail = true)
                title = element.select(".title-manga").text()
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination a:contains(»)") != null

        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/?act=searchadvance").asJsoup()

        return document.selectFirst("div.search_genres")
            ?.select("div.form-check")
            .orEmpty()
            .mapNotNull {
                val label = it.selectFirst("label")
                    ?.text() ?: return@mapNotNull null

                val value = it.selectFirst("input")
                    ?.attr("value") ?: return@mapNotNull null

                Pair(label, value)
            }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
            StatusFilter(),
            ChapterCountFilter(),
        )

        data?.parseAs<List<Pair<String, String>>>()?.also {
            filters.add(GenreFilter("Genre", it))
        }

        return FilterList(filters)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply {
            title = document.select("#title-detail-manga").text()
            thumbnail_url = document.selectFirst(".detail-info img")?.imgAttr(thumbnail = true)
            description = document.selectFirst("#summary_shortened")?.text()
            genre = document.select(".list-info a[href*=/genres/]").joinToString { it.text() }
            status = document.selectFirst(".list-info .status p:nth-child(2)")?.text().parseStatus()
            author = document.selectFirst(".list-info .author p:nth-child(2)")?.text()
                ?.takeUnless { it.trim() == "Updating" }
        }

        val updatedChapters = document.select(".wp-manga-chapter")
            .map(::chapterFromElement)

        val lastPage = document.select("div.chapters_pagination a:not(.next)").last()
            ?.attr("onclick")
            ?.run { chapterPageCountRegex.find(this)?.groupValues?.get(1) }
            ?.toIntOrNull()
            ?: return SMangaUpdate(updatedManga, updatedChapters)

        if (fetchChapters) {
            val mangaId = document.select("#title-detail-manga").attr("data-manga")

            val chapters = updatedChapters + coroutineScope {
                (2..lastPage).map { page ->
                    async {
                        val url = baseUrl.toHttpUrl().newBuilder().apply {
                            addQueryParameter("act", "ajax")
                            addQueryParameter("code", "load_list_chapter")
                            addQueryParameter("manga_id", mangaId)
                            addQueryParameter("page_num", page.toString())
                            addQueryParameter("chap_id", "0")
                            addQueryParameter("keyword", "")
                        }.build()

                        val chapterPage = client.get(url)
                            .parseAs<JsonObject>()
                            .getString("list_chap")
                            .asJsoup(baseUrl)

                        chapterPage.select(".wp-manga-chapter").map(::chapterFromElement)
                    }
                }
            }.awaitAll().flatten()

            return SMangaUpdate(updatedManga, chapters)
        } else {
            return SMangaUpdate(updatedManga, chapters)
        }
    }

    private fun String?.parseStatus(): Int {
        if (this == null) return SManga.UNKNOWN

        return when {
            contains("Complete", true) -> SManga.COMPLETED
            contains("In process", true) -> SManga.ONGOING
            contains("Pause", true) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = element.select("a").text()
        date_upload = dateFormat.tryParseDate(
            element.selectFirst(".chapter-release-date")?.text(),
        )
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return document.select("div:contains(you may also like) + div div.card > a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                with(it.selectFirst("img")!!) {
                    title = attr("alt")
                    thumbnail_url = imgAttr(thumbnail = true)
                }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val element = document.selectFirst("div.reading input#next_img_token")

        if (element != null) {
            val imgCdnUrl = document.selectFirst("div.reading #currentlink")?.attr("value")
                ?: throw Exception("Could not find image CDN URL")

            val token = element.attr("value").split(".")[1]
            val encodedImgArray = String(Base64.decode(token, Base64.DEFAULT))
                .parseAs<JsonObject>()
                .getString("data")
            val imgArray = String(Base64.decode(encodedImgArray, Base64.DEFAULT))
                .parseAs<JsonArray>()

            return imgArray.mapIndexed { i, img ->
                Page(i, imageUrl = "$imgCdnUrl/${img.string}")
            }
        }

        return document.select("div.reading-detail.box_doc img:not(noscript img)")
            .mapIndexed { i, img -> Page(i, imageUrl = img.imgAttr(thumbnail = false)!!) }
    }

    private fun Element.imgAttr(thumbnail: Boolean): String? {
        val img = attrOrNull("abs:data-cfsrc")
            ?: attrOrNull("abs:data-src")
            ?: attrOrNull("abs:data-lazy-src")
            ?: attrOrNull("abs:src")
            ?: return null

        return if (thumbnail) {
            img.toHttpUrl().newBuilder().fragment(THUMBNAIL).toString()
        } else {
            img
        }
    }
}

private const val THUMBNAIL = "thumb"
private val dateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH)
private val chapterPageCountRegex = Regex("""load_list_chapter\((\d+)\)""")
