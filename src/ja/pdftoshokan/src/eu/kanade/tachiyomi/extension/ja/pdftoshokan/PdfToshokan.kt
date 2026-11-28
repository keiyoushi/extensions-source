package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.cronet.cronet
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Source
abstract class PdfToshokan : KeiSource() {

    private val store by lazy { VolumeStore(applicationContext.cacheDir.resolve("source_$id/volumes")) }

    private val opener by lazy {
        VolumeOpener(
            store,
            VolumeDecoder(baseUrl, headers["User-Agent"]!!),
            // A volume takes minutes to come in; a stall still hits the read timeout
            client.newBuilder().callTimeout(0, TimeUnit.SECONDS).build(),
            headers,
            baseUrl,
        )
    }

    private val baseHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        // Bursts of page loads raise Cloudflare's bot score until decode.php gets challenged
        .rateLimit(2, interval = 500.milliseconds) { it.host == baseHost }
        // Well under the burst of downloads the file host was seen to take
        .rateLimit(3, period = 1.minutes) { it.host.endsWith(".$baseHost") }
        // Ahead of Cronet, which can take seconds to load, since pages come from disk
        .addInterceptor(PageInterceptor(store))
        // Cloudflare in front of the site turns away clients that don't connect like a browser
        .cronet()

    override suspend fun getPopularManga(page: Int): MangasPage = getListing(listOf("popular"), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListing(emptyList(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // Same normalization and limits as the site's own search box
        val keyword = query.replace(SPACES_REGEX, " ").trim()
        if (keyword.isNotEmpty()) {
            if (keyword.length !in 2..20) throw Exception("Enter a keyword of 2 to 20 characters")
            // One path segment, slashes included
            return getListing(listOf("search", keyword), page)
        }

        val category = filters.firstInstance<CategoryFilter>().toUriPart()
        return if (category.isNotEmpty()) {
            getListing(listOf("category") + category.split("/"), page)
        } else {
            getListing(listOfNotNull(filters.firstInstance<SortFilter>().toUriPart().ifEmpty { null }), page)
        }
    }

    private suspend fun getListing(path: List<String>, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            path.forEach(::addPathSegment)
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        }.build()
        val document = loadPage(url.toString())
        return MangasPage(document.parseListing(), document.hasPageAfter(page))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHost) return null
        val id = mangaIdOf(url.encodedPath) ?: return null
        return loadPage(titleUrl(id)).parseDetails(id)
    }

    override fun getMangaUrl(manga: SManga): String = titleUrl(manga.url)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = loadPage(getMangaUrl(manga))
        val chapterList = document.volumeLabels().map { label ->
            val number = label.toFloatOrNull()
            SChapter.create().apply {
                url = "${manga.url}/$label"
                name = if (number != null) "第${label}巻" else label
                chapter_number = number ?: -1f
            }
        }.reversed()
        return SMangaUpdate(document.parseDetails(manga.url), chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String = titleUrl(chapter.url.substringBefore("/"))

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = try {
            opener.open(getChapterUrl(chapter), chapter.url.substringAfter("/"))
        } catch (_: VolumeMissing) {
            return emptyList()
        }
        return pages.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("キーワード検索時はフィルターは無視されます"),
        Filter.Header("カテゴリー選択時は並び替えは無視されます"),
        SortFilter(),
        CategoryFilter(),
    )

    private fun titleUrl(id: String) = "$baseUrl/manga/$id"

    private suspend fun loadPage(url: String): Document = client.get(url, Fetch.DOCUMENT).asJsoup()

    private companion object {
        val SPACES_REGEX = Regex("""[　 ]""")
    }
}
