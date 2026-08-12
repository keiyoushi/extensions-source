package eu.kanade.tachiyomi.extension.vi.hv2tcomics

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HV2TComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(authInterceptor())
        addInterceptor(avifToWebpInterceptor())
        rateLimit(3)
    }

    // ================================ Auth ================================

    private var cachedAuthToken: String? = null
    private var authChecked = false

    private suspend fun loadAuthToken() {
        if (authChecked) return
        authChecked = true
        cachedAuthToken = readAuthToken()
    }

    private suspend fun readAuthToken(): String? = getLocalStorage(baseUrl, "auth_token")
        ?.takeIf { it.isNotBlank() }

    private fun authInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder().apply {
            if (original.url.host == baseUrl.toHttpUrl().host) {
                cachedAuthToken?.let { header("Authorization", "Bearer $it") }
            }
        }.build()
        chain.proceed(request)
    }

    // ============================== AVIF to WebP ==============================

    /**
     * Website have some animated avif images in thumbnail. Can't display follow bug
     * https://github.com/mihonapp/mihon/issues/1975
     * Convert first frame animated avif image to webp image
     */
    private fun avifToWebpInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()

        if (!url.endsWith(".avif", ignoreCase = true)) {
            return@Interceptor chain.proceed(request)
        }

        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            return@Interceptor response
        }

        response.body.use { body ->
            val bytes = body.bytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val source = ImageDecoder.createSource(bytes)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, source ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, outputStream)
                    val webpBytes = outputStream.toByteArray()

                    response.newBuilder()
                        .body(webpBytes.toResponseBody("image/webp".toMediaType()))
                        .build()
                } catch (_: Exception) {
                    response.newBuilder()
                        .body(bytes.toResponseBody("image/webp".toMediaType()))
                        .build()
                }
            } else {
                response.newBuilder()
                    .body(bytes.toResponseBody("image/webp".toMediaType()))
                    .build()
            }
        }
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popular")
            .build()
        return client.get(url).parseAs<ComicListResponse>().toMangasPage()
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "latest")
            .build()
        return client.get(url).parseAs<ComicListResponse>().toMangasPage()
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        loadAuthToken()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val translatorFilter = filters.firstInstanceOrNull<TranslatorFilter>()
        val genreState = genreFilter?.selectedSlugs()
        val translatorState = translatorFilter?.selectedNames()

        val urlBuilder = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "latest")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query)
        }

        genreState?.include?.let { slugs ->
            if (slugs.isNotEmpty()) urlBuilder.addQueryParameter("tags_inc", slugs.joinToString(","))
        }
        genreState?.exclude?.let { slugs ->
            if (slugs.isNotEmpty()) urlBuilder.addQueryParameter("tags_exc", slugs.joinToString(","))
        }
        translatorState?.include?.let { names ->
            if (names.isNotEmpty()) urlBuilder.addQueryParameter("translator_names_inc", names.joinToString(","))
        }
        translatorState?.exclude?.let { names ->
            if (names.isNotEmpty()) urlBuilder.addQueryParameter("translator_names_exc", names.joinToString(","))
        }

        return client.get(urlBuilder.build()).parseAs<ComicListResponse>().toMangasPage()
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        loadAuthToken()
        val detailResponse = client.get("$baseUrl/api/comics/${manga.url}").parseAs<ComicDetailResponse>()

        SMangaUpdate(
            manga = detailResponse.data.toSManga(),
            chapters = detailResponse.data.chapters.map { it.toSChapter(manga.url) },
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/truyen/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return client.get("$baseUrl/api/comics/$slug").parseAs<ComicDetailResponse>().data.toSManga()
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.name.startsWith("🔒")) {
            throw Exception("Chương này trả phí cần đăng nhập vào tài khoản phù hợp bằng webview")
        }
        loadAuthToken()
        val pageUrl = "$baseUrl/truyen/${chapter.url}"
        val imageUrls = runWebView<List<String>>(timeout = 60.seconds) {
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgent = headers["User-Agent"]!!

            var lastCount = 0
            var stablePolls = 0

            poll(1.seconds) {
                evaluateJs(
                    """
                        (function() {
                            var images = document.querySelectorAll('img[alt^="Page"]');
                            images.forEach(function(img) {
                                img.loading = 'eager';
                                img.removeAttribute('loading');
                            });
                            window.scrollTo(0, document.body.scrollHeight);
                            var urls = [];
                            images.forEach(function(img) {
                                if (img.src && img.src.includes('/media/')) {
                                    urls.push(img.src);
                                }
                            });
                            return urls;
                        })()
                    """.trimIndent(),
                ) { result ->
                    val urls = result.parseAs<List<String>>()
                    if (urls.isNotEmpty()) {
                        if (urls.size == lastCount) {
                            stablePolls++
                        } else {
                            lastCount = urls.size
                            stablePolls = 0
                        }
                        if (stablePolls >= 3) {
                            resolve(urls.distinct())
                        }
                    }
                }
                evaluateJs(
                    """
                        (function() {
                            var loginRequired = document.querySelector('h2')?.textContent?.includes('Yêu cầu đăng nhập');
                            return loginRequired;
                        })()
                    """.trimIndent(),
                ) { result ->
                    if (result != "null" && result.parseAs<Boolean>()) {
                        reject(Exception("Đăng nhập vào tài khoản phù hợp bằng webview để xem chương này"))
                    }
                }
            }
            loadUrl(pageUrl)
        }
        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, pageUrl, imageUrl)
        }
    }

    // ============================== Filters ================================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        loadAuthToken()
        val tags = async { client.get("$baseUrl/api/tags").parseAs<TagResponse>().data.map { TagOption(it.id, it.name, it.slug) } }
        val translators = async {
            client.get("$baseUrl/api/translators?view=top-by-comics-v1")
                .parseAs<TranslatorResponse>()
                .data
                .map { TranslatorOption(it) }
        }

        FilterData(
            tags = tags.await(),
            translators = translators.await(),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        return getFilters(filterData?.tags, filterData?.translators)
    }

    // ============================= Utilities ==============================

    private fun ComicListResponse.toMangasPage(): MangasPage = MangasPage(
        mangas = data.map { it.toSManga() },
        hasNextPage = meta.page < meta.totalPages,
    )
}
