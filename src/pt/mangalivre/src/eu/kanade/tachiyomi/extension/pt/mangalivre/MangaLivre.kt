package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
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
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaLivre :
    KeiSource(),
    ConfigurableSource {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    private val apiUrl: String get() = "$baseUrl/api"

    private val preferences by getPreferencesLazy()
    private val verificationMutex = Mutex()

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Accept", "*/*")
        .set("Accept-Language", "pt-BR,en-US;q=0.9,en;q=0.8")
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-origin")

    // ============================== Popular =======================================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            listOf(
                OrderByFilter(options = listOf("" to SORT_POPULAR)),
                OrderDirectionFilter(options = listOf("" to DIRECTION_DESC)),
            ),
        ),
    )

    // ============================== Latest =======================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            listOf(
                OrderByFilter(options = listOf("" to SORT_UPDATED)),
                OrderDirectionFilter(options = listOf("" to DIRECTION_DESC)),
            ),
        ),
    )

    // ============================== Search =======================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url =
            "$apiUrl/mangas/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", "24")

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    url.addQueryParameter("sortBy", filter.selected())
                }
                is OrderDirectionFilter -> {
                    url.addQueryParameter("sortOrder", filter.selected())
                }
                else -> {}
            }
        }
        val dto = client.get(url.build()).parseJson<WrapperDto>()
        val mangas = dto.mangas.map { it.toSManga(useAlternativeTitle) }
        return MangasPage(mangas, dto.hasNextPage)
    }

    // ============================== Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$apiUrl/manga-by-slug/${manga.url}").parseJson<MangaDto>()
        return SMangaUpdate(
            manga = dto.toSManga(useAlternativeTitle),
            chapters = dto.toSChapterList(),
        )
    }

    // ============================== Pages =======================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val ref = chapter.memo.parseAs<ChapterReferenceDto>()
        val chapterNumber = chapterUrl.pathSegments.last { it.isNotEmpty() }

        return verificationMutex.withLock {
            fetchReaderAccess(ref)?.let { access ->
                return@withLock access.chapter.pages.toPageList(ref.mangaId, chapterNumber)
            }

            openVerificationWebView(chapterUrl.toString(), ref.mangaId, chapterNumber)
                .toPageList(ref.mangaId, chapterNumber)
        }
    }

    private suspend fun fetchReaderAccess(ref: ChapterReferenceDto): ReaderAccessResponseDto? {
        client.post(
            "$apiUrl/reader/chapter/access",
            body = ref.toJsonRequestBody(),
            ensureSuccess = false,
        ).use { response ->
            if (response.isSuccessful) return response.parseAs()

            val error = response.parseAs<ReaderAccessErrorDto>()
            if (response.code == 403 && error.error == READER_VERIFICATION_REQUIRED) return null
            throw IOException(error.error)
        }
    }

    private suspend fun openVerificationWebView(
        readerUrl: String,
        mangaId: String,
        chapterNumber: String,
    ): List<String> {
        val result = CompletableDeferred<List<String>>()
        val receiver =
            object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(
                    resultCode: Int,
                    resultData: Bundle?,
                ) {
                    val pages = resultData?.getStringArrayList(ReaderVerificationActivity.EXTRA_PAGES).orEmpty()
                    if (resultCode == ReaderVerificationActivity.RESULT_PAGES && pages.isNotEmpty()) {
                        result.complete(pages)
                    } else {
                        result.completeExceptionally(IOException("Verificação cancelada."))
                    }
                }
            }
        val intent =
            Intent().apply {
                component = ComponentName(EXTENSION_PACKAGE, ReaderVerificationActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ReaderVerificationActivity.EXTRA_URL, readerUrl)
                putExtra(ReaderVerificationActivity.EXTRA_MANGA_ID, mangaId)
                putExtra(ReaderVerificationActivity.EXTRA_CHAPTER_NUMBER, chapterNumber)
                putExtra(ReaderVerificationActivity.EXTRA_RECEIVER, receiver)
            }
        applicationContext.startActivity(intent)
        return withTimeout(VERIFICATION_TIMEOUT) { result.await() }
    }

    // ============================== Filters =======================================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOf(
            OrderByFilter(
                "Ordem",
                listOf(
                    "Mais Visualizados" to SORT_POPULAR,
                    "Lançamentos" to SORT_RELEASE,
                    "Última Atualização" to SORT_UPDATED,
                    "Melhor Avaliação" to SORT_RATING,
                    "A-Z" to SORT_TITLE,
                ),
            ),
            Filter.Separator(),
            OrderDirectionFilter(
                "Direção",
                listOf(
                    "↑ Decrescente" to DIRECTION_DESC,
                    "↓ Crescente" to DIRECTION_ASC,
                ),
            ),
        ),
    )

    val useAlternativeTitle: Boolean get() =
        preferences.getBoolean(ALTERNATIVE_TITLE_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context)
            .apply {
                key = ALTERNATIVE_TITLE_PREF
                title = "Titulo alternativo"
                summary =
                    buildString {
                        append("Use titulos alternativos como principal quando disponivel.")
                        append(" Essa opção não tem efeito sobre obras já adicionadas na sua biblioteca")
                    }
                setDefaultValue(false)
            }.also(screen::addPreference)
    }

    // ============================== Utilities =======================================

    private inline fun <reified T> Response.parseJson(): T {
        val peek = peekBody(MAX_PEEK).string().trimStart()
        if (peek.isEmpty() || peek.startsWith("<")) {
            close()
            throw IOException(NON_JSON_MESSAGE)
        }
        return parseAs<T>()
    }

    companion object {
        private val VERIFICATION_TIMEOUT = 2.minutes
        private const val EXTENSION_PACKAGE = "eu.kanade.tachiyomi.extension.pt.mangalivre"
        private const val CDN_HOST = "cdn.toonlivre.net"
        private const val PROXY_HOST = "slightly-free-mayfly.edgecompute.app"
        private val PAGE_NUMBER_REGEX = Regex("""_(\d+)\.[^.]+$""")

        private const val ALTERNATIVE_TITLE_PREF = "alternativeTitlePref"
        private const val MAX_PEEK = 1024L
        private const val NON_JSON_MESSAGE =
            "Resposta não-JSON (Cloudflare ou header desatualizado). Abra a fonte na WebView do app e tente de novo."
        private const val READER_VERIFICATION_REQUIRED = "Reader verification required"

        private const val SORT_POPULAR = "popular"
        private const val SORT_RELEASE = "release"
        private const val SORT_UPDATED = "updated"
        private const val SORT_RATING = "rating"
        private const val SORT_TITLE = "title"
        private const val DIRECTION_DESC = "desc"
        private const val DIRECTION_ASC = "asc"
    }

    private fun String.toCdnImageUrl(): String? {
        val url = toHttpUrlOrNull() ?: return null
        val candidate =
            when (url.host) {
                CDN_HOST -> url
                PROXY_HOST -> url.queryParameter("url")?.toHttpUrlOrNull()
                else -> null
            } ?: return null

        return candidate.takeIf { it.isHttps && it.host == CDN_HOST }?.toString()
    }

    private fun String.isChapterImage(
        mangaId: String,
        chapterNumber: String,
    ): Boolean {
        val pathSegments = toHttpUrl().pathSegments
        return pathSegments.size >= 4 &&
            pathSegments[0] == "obras" &&
            pathSegments[1] == mangaId &&
            pathSegments[2] == chapterNumber &&
            pathSegments[3].isNotEmpty()
    }

    private fun List<String>.toPageList(
        mangaId: String,
        chapterNumber: String,
    ): List<Page> {
        val sortedUrls =
            asSequence()
                .mapNotNull { it.toCdnImageUrl() }
                .filter { it.isChapterImage(mangaId, chapterNumber) }
                .distinct()
                .sortedWith(compareBy<String>({ it.pageNumber() ?: Int.MAX_VALUE }, { it }))
                .toList()
        return sortedUrls.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private fun String.pageNumber(): Int? = toHttpUrl()
        .pathSegments
        .lastOrNull()
        ?.let(PAGE_NUMBER_REGEX::find)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}
