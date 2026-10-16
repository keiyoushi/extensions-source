package eu.kanade.tachiyomi.extension.ja.soraraw

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

@Source
abstract class SoraRaw :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val showAdult get() = preferences.getBoolean(PREF_ADULT, false)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::descrambleImage)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getRanking("top/last7Days.json")

    // The site's 18+ setting swaps every ranking for its 18+ list
    private suspend fun getRanking(path: String): MangasPage {
        val ranking = client.get("$baseUrl/$path").parseAs<RankingDto>()
        val mangas = if (showAdult) ranking.manga18s else ranking.mangas
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListing("newest", page)

    private suspend fun getListing(path: String, page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/$path" else "$baseUrl/$path/page/$page"
        val listing = client.get(url).extractNextJs<ListingDto>()!!
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

    // The site's own search runs over these catalog files in the browser
    private suspend fun searchCatalog(query: String, genre: Genre?): MangasPage = coroutineScope {
        val pages = client.get("$baseUrl/config.json").parseAs<ConfigDto>().catalogPages
        val entries = (1..pages).map { page ->
            async { client.get("$baseUrl/mangas_$page.json").parseAs<CatalogDto>().list }
        }.awaitAll().flatten()

        val words = query.split(WHITESPACE)
        val mangas = entries
            .filter { entry ->
                val text = listOfNotNull(entry.name, entry.altNames, entry.author).joinToString(" ")
                words.all { text.contains(it, ignoreCase = true) }
            }
            .filter { genre == null || it.genres.any(genre.ids::contains) }
            .filter { showAdult || !it.isAdult }
            .sortedByDescending { it.views }
            .map { it.toSManga() }
        MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // =============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get(getMangaUrl(manga)).extractNextJs<MangaPageDto>()!!.manga
        return SMangaUpdate(details.toSManga(), details.toSChapterList())
    }

    // =============================== Pages ================================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter)).extractNextJs<ChapterPageDto>()!!.chapter
        val manifestBase = client.get("$baseUrl/config.json").parseAs<ConfigDto>().apiImage
        val manifestUrl = manifestBase.toHttpUrl().newBuilder()
            .addPathSegment(data.mangaId.toString())
            .addPathSegment("${data.id}.json")
            .addQueryParameter("t", Instant.parse(data.updatedAt).toEpochMilliseconds().toString())
            .build()
        val manifest = client.get(manifestUrl).parseAs<ManifestDto>().d
            .decodeBase64()
            .xorInPlace(MANIFEST_KEY.toByteArray())
            .decodeToString()
            .trimEnd('\u0000')
            .parseAs<List<ManifestEntryDto>>()

        val fragment = when (data.mode) {
            "canva" -> "$CANVA=${data.id}:${data.token!!}"
            "canva2" -> "$CANVA2=${data.id}"
            else -> null
        }
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

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/genres.json").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        // The site lists one genre several times under the same slug
        val genres = data?.parseAs<List<GenreDto>>()
            ?.filter { it.slug.isNotEmpty() && (showAdult || !it.isAdult) }
            ?.groupBy { it.slug }
            ?.map { (slug, entries) ->
                val main = entries.maxBy { it.total }
                Genre(main.name, slug, entries.mapTo(HashSet()) { it.id }, entries.sumOf { it.total })
            }
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
        val (mode, args) = request.url.fragment?.split('=', limit = 2)
            ?.takeIf { it.size == 2 && it[0] in setOf(CANVA, CANVA2) }
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

        val result = if (mode == CANVA) {
            val (chapterId, token) = args.split(':', limit = 2)
            CanvaDescrambler.descramble(pixels, width, height, chapterId, token)
        } else {
            Canva2Descrambler.descramble(pixels, width, height, args)
        }

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
        private const val CANVA = "canva"
        private const val CANVA2 = "canva2"
        private val JPEG = "image/jpeg".toMediaType()
        private val WHITESPACE = Regex("""\s+""")
    }
}
