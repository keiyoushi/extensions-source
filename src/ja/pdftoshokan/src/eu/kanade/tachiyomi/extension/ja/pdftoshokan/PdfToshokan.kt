package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cronet.cronet
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Source
abstract class PdfToshokan : KeiSource() {

    private val preferences by getPreferencesLazy()

    private val store by lazy { VolumeStore(applicationContext.cacheDir.resolve("source_$id/volumes")) }

    private val fileHost by lazy { FileHost(client, headers, baseUrl, preferences) }

    private val decoder by lazy { VolumeDecoder(baseUrl, headers["User-Agent"]!!) }

    private val downloads = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // decode.php's latest pass is the only one the file host takes, so a volume is fetched before the next is decoded
    private val decodeLock = Mutex()

    private val baseHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        // Bursts of page loads raise Cloudflare's bot score until decode.php gets challenged
        .rateLimit(2, interval = 500.milliseconds) { it.host == baseHost }
        // Well under the burst of downloads the file host was seen to take
        .rateLimit(3, 1.minutes) { it.host.endsWith(".$baseHost") }
        // Cloudflare in front of the site turns away clients that don't connect like a browser
        .cronet()
        .addInterceptor(PageInterceptor(store) { it.host.endsWith(".$baseHost") })

    override suspend fun getPopularManga(page: Int): MangasPage = getListing("popular", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListing("", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // Same normalization and limits as the site's own search box
        val keyword = query.replace(SPACES_REGEX, " ").trim()
        if (keyword.isNotEmpty()) {
            if (keyword.length !in 2..20) throw Exception("キーワードは2〜20文字で入力してください")
            return getListing("search/$keyword", page)
        }

        val category = filters.firstInstance<CategoryFilter>().toUriPart()
        return if (category.isNotEmpty()) {
            getListing("category/$category", page)
        } else {
            getListing(filters.firstInstance<SortFilter>().toUriPart(), page)
        }
    }

    private suspend fun getListing(path: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (path.isNotEmpty()) addPathSegments(path)
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
            SChapter.create().apply {
                url = "${manga.url}/$label"
                name = if (label.toFloatOrNull() != null) "第${label}巻" else label
                chapter_number = label.toFloatOrNull() ?: -1f
            }
        }.reversed()
        return SMangaUpdate(document.parseDetails(manga.url), chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String = titleUrl(chapter.url.substringBefore("/"))

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (file, count) = openVolume(chapter.url.substringBefore("/"), chapter.url.substringAfter("/"))
        return List(count) { Page(it, imageUrl = "$file#$it") }
    }

    /**
     * A stored volume opens from its remembered file without going online. Any other goes through decode.php, which
     * hands out the pass the file host asks for along with the file, the way the site's read button does.
     */
    private suspend fun openVolume(id: String, label: String): Pair<HttpUrl, Int> {
        val known = preferences.getString(fileKey(id, label), null)?.toHttpUrl()
            ?: preferences.getString(folderKey(id), null)?.toHttpUrl()?.volumeFile(label)
        if (known != null && store.contains(known)) return known to store.pageCount(known)

        return decodeLock.withLock {
            val file = decoder.decode(titleUrl(id), label)
            remember(id, label, file)
            store.download(file, downloads) { fileHost.download(file, it) }
            // The page count comes in with the file, so the file host has taken the pass by then
            file to store.pageCount(file)
        }
    }

    /** A file named after its volume is covered by its title's folder, so only other names are kept. */
    private fun remember(id: String, label: String, file: HttpUrl) {
        preferences.edit()
            .apply {
                if (file.pathSegments.last() == "$label.pdf") remove(fileKey(id, label)) else putString(fileKey(id, label), file.toString())
            }
            .putString(folderKey(id), file.newBuilder().setPathSegment(file.pathSize - 1, "").build().toString())
            .apply()
    }

    private fun HttpUrl.volumeFile(label: String) = newBuilder().setPathSegment(pathSize - 1, "$label.pdf").build()

    private fun fileKey(id: String, label: String) = "file_$id/$label"

    private fun folderKey(id: String) = "folder_$id"

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("キーワード検索時はフィルターは無視されます"),
        Filter.Header("カテゴリー選択時は並び替えは無視されます"),
        SortFilter(),
        CategoryFilter(),
    )

    private fun titleUrl(id: String) = "$baseUrl/manga/$id"

    private suspend fun loadPage(url: String): Document = client.get(url, Fetch.DOCUMENT).asJsoup()

    companion object {
        private val SPACES_REGEX = Regex("""[　 ]""")
    }
}
