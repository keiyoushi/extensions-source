package eu.kanade.tachiyomi.extension.ja.mangaruu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesCtr
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.decodeHex
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.xorInPlace
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import kotlin.time.Instant

// Another frontend of the SoraRaw backend: same catalog files, image manifests and reader scrambling
@Source
abstract class Mangaruu :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    private val showAdult get() = preferences.getBoolean(PREF_ADULT, false)

    private var apiImage: String? = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::descrambleImage)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getRanking("top/last7Days.json")

    // The site's 18+ setting swaps every ranking for its 18+ list
    private suspend fun getRanking(path: String): MangasPage {
        val ranking = client.get("$baseUrl/$path", Fetch.XHR).parseAs<RankingDto>()
        val mangas = if (showAdult) ranking.manga18s else ranking.mangas
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListing("", page)

    private suspend fun getListing(path: String, page: Int): MangasPage {
        val url = buildString {
            append(baseUrl)
            if (path.isNotEmpty()) append('/', path)
            if (page > 1) append("/page/", page)
        }
        val listing = client.get(url, Fetch.DOCUMENT).extractNextJs<ListingDto>()!!
        return MangasPage(listing.results.map { it.toSManga() }, listing.hasNextPage)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        if (query.isNotBlank()) return searchCatalog(query.trim(), genre)
        if (genre != null) return getListing("genre/${genre.slug}", page)

        val path = filters.firstInstance<ListFilter>().path
        return if (path.startsWith("top/")) {
            getRanking(path)
        } else {
            getListing(path, page)
        }
    }

    // The site's own search runs over these catalog files in the browser, which stop at the first 404
    private suspend fun searchCatalog(query: String, genre: Genre?): MangasPage {
        val entries = buildList {
            var page = 1
            while (true) {
                val response = client.get("$baseUrl/mangas_$page.json", Fetch.XHR, ensureSuccess = false)
                if (!response.isSuccessful) {
                    response.close()
                    break
                }
                addAll(response.parseAs<CatalogDto>().list)
                page++
            }
        }

        val words = query.split(WHITESPACE)
        val mangas = entries
            .filter { entry ->
                val text = listOfNotNull(entry.name, entry.altNames, entry.author).joinToString(" ")
                words.all { text.contains(it, ignoreCase = true) }
            }
            .filter { genre == null || genre.id in it.genres }
            .filter { showAdult || !it.isAdult }
            .sortedByDescending { it.views }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "m") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // =============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/m/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get(getMangaUrl(manga), Fetch.DOCUMENT).extractNextJs<MangaPageDto>()!!.manga
        return SMangaUpdate(details.toSManga(), details.toSChapterList())
    }

    // =============================== Pages ================================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/c/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).extractNextJs<ChapterPageDto>()!!.chapter
        val manifestUrl = getApiImage().toHttpUrl().newBuilder()
            .addPathSegment(data.manga.id.toString())
            .addPathSegment("${data.id}.json")
            .addQueryParameter("t", Instant.parse(data.updatedAt).toEpochMilliseconds().toString())
            .build()
        val manifest = client.get(manifestUrl, Fetch.XHR).parseAs<ManifestDto>().d
            .decodeBase64()
            .xorInPlace(MANIFEST_KEY.toByteArray())
            .decodeToString()
            .trimEnd('\u0000')
            .parseAs<List<ManifestEntryDto>>()

        val fragment = if (data.mode == CANVA2) "$CANVA2=${data.id}" else null
        val key = data.uuid.decodeHex()
        val imageHost = data.imageHost.toHttpUrl()
        return manifest.sortedBy { it.order }.mapIndexed { i, entry ->
            val path = entry.b.decodeBase64().xorInPlace(IMAGE_KEY.toByteArray()).let {
                it.copyOfRange(16, it.size).aesDecrypt(aesCtr(key, it.copyOfRange(0, 16))).decodeToString()
            }
            val imageUrl = imageHost.newBuilder().addPathSegments(path).fragment(fragment).build()
            Page(i, imageUrl = imageUrl.toString())
        }
    }

    private suspend fun getApiImage(): String = apiImage
        ?: client.get("$baseUrl/config.json", Fetch.XHR).parseAs<ConfigDto>().apiImage.also { apiImage = it }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/genres.json", Fetch.XHR).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()
            ?.filter { it.slug.isNotEmpty() }
            ?.map { Genre(it.name, it.slug, it.id, it.total) }
            ?.sortedByDescending { it.total }
        return FilterList(
            listOfNotNull(
                ListFilter(),
                Filter.Separator(),
                Filter.Header("ジャンルを選ぶと一覧より優先されます"),
                Filter.Header("キーワード検索ではジャンルのみ使われます"),
                genres?.let { GenreFilter(it) },
            ),
        )
    }

    private fun descrambleImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterId = request.url.fragment?.takeIf { it.startsWith("$CANVA2=") }?.substringAfter('=')
            ?: return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val bitmap = response.use { BitmapFactory.decodeStream(it.body.byteStream()) }
            ?: throw IOException("Failed to decode image")
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val result = Canva2Descrambler.descramble(pixels, width, height, chapterId)

        val output = Buffer()
        Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888).apply {
            compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
            recycle()
        }
        return response.newBuilder()
            .removeHeader("Content-Length")
            .removeHeader("Content-Type")
            .body(output.asResponseBody(JPEG, output.size))
            .build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT
            title = "18+作品を表示"
            summary = "サイトの18+設定と同じです（ランキングは18+版に切り替わります）"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private fun String.decodeBase64(): ByteArray = Base64.decode(replace('-', '+').replace('_', '/'), Base64.DEFAULT)

    companion object {
        private const val PREF_ADULT = "show_adult"
        private const val MANIFEST_KEY = "/fuCkYou!!!"
        private const val IMAGE_KEY = "202508055d0db38bae2e86cc41649f90"
        private const val CANVA2 = "canva2"
        private val JPEG = "image/jpeg".toMediaType()
        private val WHITESPACE = Regex("""\s+""")
    }
}
