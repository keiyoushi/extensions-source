package eu.kanade.tachiyomi.extension.ja.zebrack

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Zebrack :
    KeiSource(),
    ConfigurableSource {
    private val subdomain = "zebrack-comic"
    private val apiUrl = "https://api2.$subdomain.com/api"
    private val magazineApiUrl = "https://api.$subdomain.com/api"
    private val preferences by getPreferencesLazy()
    private val jst = ZoneId.of("Asia/Tokyo")
    private val hideLocked get() = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
    private val secretMutex = Mutex()

    @Volatile
    private var secret: String? = null

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor(::secretIntercept)
    }

    private fun secretIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSessionExpired()) return response

        response.close()

        val failedSecret = request.url.queryParameter("secret")

        clearSecretIfCurrent(failedSecret)

        val newSecret = runBlocking {
            flushSecret(failedSecret)
            fetchSecret()
        }
        val isSecretValid = !newSecret.isNullOrEmpty() && newSecret != failedSecret
        val newUrl = request.url.newBuilder().apply {
            if (isSecretValid) {
                setQueryParameter("secret", newSecret)
            } else {
                removeAllQueryParameters("secret")
                clearSecretIfCurrent(newSecret)
            }
        }.build()

        val newRequest = request.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/v3/title_tab_view".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("type", "ranking")
            .build()

        val result = client.get(url).parseAsProto<RankingResponse>()
        val mangas = result.list.filter { it.type == "総合" }.flatMap { it.titles }.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getUpdateDay(currentDayInJapan)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/v3/title_search".toHttpUrl().newBuilder()
                .addQueryParameter("os", "browser")
                .addQueryParameter("search_order", "related")
                .addQueryParameter("keyword", query)
                .build()
            return client.get(url).toMangasPage()
        }

        val filter = filters.firstInstance<CategoryFilter>()
        return when (filter.type) {
            "day" -> getUpdateDay(filter.value)
            "magazine" -> {
                val url = "$magazineApiUrl/browser/${filter.value}".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .build()

                val result = client.get(url).parseAsProto<MagazineFilterResponse>()
                val mangas = with(result.magazines) {
                    (magazinesListAll + magazinesListMen + magazinesListWoman).map { it.toSManga() }
                }
                MangasPage(mangas, false)
            }
            else -> {
                val url = "$apiUrl/v3/title_tag_search".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .addQueryParameter("tag_id", filter.value)
                    .addQueryParameter("search_order", "popular")
                    .build()

                client.get(url).toMangasPage()
            }
        }
    }

    private suspend fun getUpdateDay(day: String): MangasPage {
        val url = "$apiUrl/v3/rensai".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("day", day)
            .build()

        val mangas = client.get(url).parseAsProto<LatestResponse>().list.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private fun Response.toMangasPage(): MangasPage {
        val mangas = this.parseAsProto<SearchResponse>().list.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val isMagazine = manga.isMagazine

        val details = async {
            if (!fetchDetails) return@async manga
            if (isMagazine) getMagazineDetails(manga.url) else getTitleDetails(manga.url)
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            if (isMagazine) getMagazineChapters(manga.url) else getTitleChapters(manga.url)
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    private suspend fun getTitleDetails(titleId: String): SManga {
        val url = "$apiUrl/browser/title_detail".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("title_id", titleId)
            .addQueryParameter("tab", "detail")
            .build()

        return client.get(url).parseAsProto<MangaDetailsResponse>().details.toSManga()
    }

    private suspend fun getMagazineDetails(magazineId: String): SManga {
        val url = "$apiUrl/v3/magazine_detail".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("magazine_id", magazineId)
            .build()

        return client.get(url).parseAsProto<MagazineDetailsResponse>().details.toSManga()
    }

    private suspend fun getTitleChapters(titleId: String): List<SChapter> = coroutineScope {
        val secretKey = fetchSecret()

        val volumes = async {
            val url = "$apiUrl/browser/title_volume_list".toHttpUrl().newBuilder()
                .addQueryParameter("os", "browser")
                .addQueryParameter("title_id", titleId)
                .apply { secretKey?.let { addQueryParameter("secret", it) } }
                .build()

            val result = client.get(url).parseAsProto<VolumeResponse>()
            checkSessionExpired(result.volumeData?.volumeList?.firstNotNullOfOrNull { it.session?.message }, secretKey)
            result.volumeData?.volumeList.orEmpty()
                .filter { !hideLocked || !it.isLockedVolume }
                .map { it.toSChapter() }
        }

        val chapters = async {
            val url = "$apiUrl/v3/title_chapter_list".toHttpUrl().newBuilder()
                .addQueryParameter("os", "browser")
                .addQueryParameter("title_id", titleId)
                .apply { secretKey?.let { addQueryParameter("secret", it) } }
                .build()

            val result = client.get(url).parseAsProto<ChapterResponse>()
            val chapterList = result.chapterList.orEmpty().flatMap { it.chapters.orEmpty() }
            checkSessionExpired(chapterList.firstNotNullOfOrNull { it.session?.message }, secretKey)
            chapterList
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter() }
        }

        (volumes.await() + chapters.await()).reversed()
    }

    private suspend fun getMagazineChapters(magazineId: String): List<SChapter> {
        val secretKey = fetchSecret()
        val chapters = mutableListOf<SChapter>()
        val latestYear = LocalDate.now(jst).year

        for (year in latestYear downTo latestYear - MAGAZINE_YEARS) {
            val url = "$apiUrl/browser/magazine_backnumbers".toHttpUrl().newBuilder()
                .addQueryParameter("os", "browser")
                .addQueryParameter("magazine_id", magazineId)
                .addQueryParameter("year", year.toString())
                .apply { secretKey?.let { addQueryParameter("secret", it) } }
                .build()

            val issues = client.get(url).parseAsProto<MagazineResponse>().magazineData?.magazineList
            checkSessionExpired(issues?.firstNotNullOfOrNull { it.session?.message }, secretKey)
            if (issues.isNullOrEmpty()) break

            chapters += issues.filter { !hideLocked || !it.isLockedMagazine }.map { it.toSChapter() }
        }

        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val secretKey = fetchSecret()
        val memo = chapter.memo

        val pages = when (chapter.type) {
            TYPE_CHAPTER -> {
                val body = FormBody.Builder() // application/x-www-form-urlencoded
                    .add("os", "browser")
                    .add("title_id", memo["titleId"]!!.string)
                    .add("chapter_id", chapter.url)
                    .add("type", "normal")
                    .apply { secretKey?.let { add("secret", it) } }
                    .build()

                client.post("$apiUrl/v3/chapter_viewer", body).toPageList(secretKey)
            }

            TYPE_VOLUME -> {
                val url = "$apiUrl/v3/manga_volume_viewer".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .addQueryParameter("title_id", memo["titleId"]!!.string)
                    .addQueryParameter("volume_id", chapter.url)
                    .apply { secretKey?.let { addQueryParameter("secret", it) } }
                    .addQueryParameter("is_trial", chapter.isTrial)
                    .build()

                client.get(url).toPageList(secretKey)
            }

            else -> {
                val url = "$magazineApiUrl/browser/magazine_viewer".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .addQueryParameter("magazine_id", memo["magazineId"]!!.string)
                    .addQueryParameter("magazine_issue_id", chapter.url)
                    .apply { secretKey?.let { addQueryParameter("secret", it) } }
                    .addQueryParameter("is_trial", chapter.isTrial)
                    .build()

                val result = client.get(url).parseAsProto<MagazineViewerImages>()
                checkSessionExpired(result.session?.message, secretKey)
                result.pages?.pagesList.orEmpty().mapIndexedNotNull { i, image ->
                    image.page?.let { Page(i, imageUrl = "$it#key=${image.key}") }
                }
            }
        }

        return pages.ifEmpty { throw Exception(LOCKED) }
    }

    private suspend fun Response.toPageList(secretKey: String?): List<Page> {
        val result = parseAsProto<ViewerResponse>()
        checkSessionExpired(result.session?.message, secretKey)
        return result.images.mapIndexedNotNull { i, image ->
            image.pages?.let { Page(i, imageUrl = "${it.page}#key=${it.key}") }
        }
    }

    override fun getMangaUrl(manga: SManga): String = if (manga.isMagazine) {
        "$baseUrl/magazine/${manga.url}/detail"
    } else {
        "$baseUrl/title/${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val memo = chapter.memo
        return when (chapter.type) {
            TYPE_CHAPTER -> "$baseUrl/title/${memo["titleId"]!!.string}/chapter/${chapter.url}/viewer"
            TYPE_VOLUME -> "$baseUrl/title/${memo["titleId"]!!.string}/volume/${chapter.url}/viewer"
            else -> "$baseUrl/magazine/${memo["magazineId"]!!.string}/issue/${chapter.url}/viewer"
        }
    }

    private val SManga.isMagazine: Boolean get() = memo["type"]?.string == TYPE_MAGAZINE
    private val SChapter.type: String get() = memo["type"]?.string ?: TYPE_CHAPTER
    private val SChapter.isTrial: String get() = if (memo["trial"]?.boolean == true) "1" else "0"

    private val currentDayInJapan: String
        get() = WEEKDAYS[LocalDate.now(jst).dayOfWeek.value - 1]

    private suspend fun fetchSecret(): String? {
        secret?.let { return it }

        return secretMutex.withLock {
            secret ?: getLocalStorage("$baseUrl/", SECRET_STORAGE_KEY)
                ?.takeUnless { it.isBlank() }
                ?.also { secret = it }
        }
    }

    private suspend fun flushSecret(target: String?) {
        val key = SECRET_STORAGE_KEY.toJsonString()
        val script = "if(localStorage.getItem($key)===${target.toJsonString()}){localStorage.removeItem($key)}"

        runWebView(timeout = 10.seconds) {
            onPageFinished {
                evaluateJs(script) { resolve(Unit) }
            }
            loadData("$baseUrl/", "")
        }
    }

    private fun Response.isSessionExpired(): Boolean = try {
        peekBody(Long.MAX_VALUE).string().contains(SESSION_EXPIRED)
    } catch (_: Exception) {
        false
    }

    private suspend fun checkSessionExpired(sessionMsg: String?, failedSecret: String?) {
        if (sessionMsg == SESSION_EXPIRED) {
            clearSecretIfCurrent(failedSecret)
            flushSecret(failedSecret)
            throw Exception(LOCKED)
        }
    }

    private fun clearSecretIfCurrent(value: String?) {
        if (secret == value) secret = null
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val SECRET_STORAGE_KEY = "device_secret_key"
        private const val SESSION_EXPIRED = "ログイン期限切れ"
        private const val LOCKED = "Log in via WebView and purchase this product to read."
        private const val MAGAZINE_YEARS = 30

        private val WEEKDAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    }
}
