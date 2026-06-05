package eu.kanade.tachiyomi.extension.tr.holyscans

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.util.Calendar
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class HolyScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Holy Scans"

    override val baseUrl = "https://holyscans.com.tr"

    override val lang = "tr"

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    private val loginMutex = ReentrantLock()

    override val client = network.client.newBuilder()
        .addInterceptor(::loginInterceptor)
        .build()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val form = FormBody.Builder()
            .add("action", "filter_manga_archive")
            .add("paged", page.toString())
            .build()

        val referer = "$baseUrl/manga/?m_orderby=views"
        val popularHeaders = headersBuilder().set("Referer", referer).build()
        return POST("$baseUrl/wp-admin/admin-ajax.php", popularHeaders, form)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseAjaxMangaList(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val form = FormBody.Builder()
                .add("action", "holy_live_search")
                .add("keyword", query)
                .build()
            return POST("$baseUrl/wp-admin/admin-ajax.php", headers, form)
        }

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
        val types = filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()

        val form = FormBody.Builder()
            .add("action", "filter_manga_archive")
            .add("paged", page.toString())

        genres.forEach { form.add("genres[]", it) }
        types.forEach { form.add("types[]", it) }
        statuses.forEach { form.add("statuses[]", it) }

        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, form.build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestBody = response.request.body
        if (requestBody is FormBody) {
            val action = (0 until requestBody.size).find { requestBody.name(it) == "action" }?.let { requestBody.value(it) }
            if (action == "holy_live_search") {
                val dto = response.parseAs<LiveSearchResponse>()
                val document = Jsoup.parseBodyFragment(dto.data, baseUrl)
                val mangas = document.select("a.holy-live-result-item").map { element ->
                    SManga.create().apply {
                        setUrlWithoutDomain(element.absUrl("href"))
                        title = element.select("span").text()
                        thumbnail_url = element.select("img").attr("abs:src")
                    }
                }
                return MangasPage(mangas, false)
            }
        }

        return parseAjaxMangaList(response)
    }

    private fun parseAjaxMangaList(response: Response): MangasPage {
        val dto = response.parseAs<AjaxResponse>()
        val document = Jsoup.parseBodyFragment(dto.htmlContent, baseUrl)
        val mangas = document.select(".manga-card-v2").map { element ->
            SManga.create().apply {
                val titleLink = element.selectFirst(".mc-title a")
                if (titleLink != null) {
                    setUrlWithoutDomain(titleLink.absUrl("href"))
                    title = titleLink.text()
                } else {
                    val imageBox = element.selectFirst(".mc-image-box")!!
                    setUrlWithoutDomain(imageBox.attr("abs:data-href"))
                    title = imageBox.selectFirst("img")!!.attr("alt")
                }
                thumbnail_url = element.selectFirst(".mc-image-box img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, dto.hasNext)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".manga-main-title")?.text() ?: throw Exception("Manga başlığı bulunamadı")
            thumbnail_url = document.selectFirst(".manga-cover-area img")?.attr("abs:src")
            author = document.selectFirst(".detail-box:contains(Yazar) .d-val")?.text()
            artist = document.selectFirst(".detail-box:contains(Çizer) .d-val")?.text()
            genre = document.select(".detail-box:contains(Türler) a").joinToString { it.text() }
            status = when (document.selectFirst(".detail-box:contains(Durum) .d-val")?.text()?.lowercase()) {
                "devam ediyor" -> SManga.ONGOING
                "tamamlandı", "final" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = document.selectFirst(".manga-summary-content")?.text()
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".manga-chapter-list-wrap .ch-list-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".ch-title")?.text() ?: throw Exception("Bölüm adı bulunamadı")
                date_upload = parseRelativeDate(element.selectFirst(".ch-date")?.text() ?: "")
            }
        }
    }

    // =============================== Pages ===============================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .flatMap { response ->
                val documentHtml = response.body.string()

                val chapterId = CHAPTER_ID_REGEX.find(documentHtml)?.groupValues?.get(1)
                    ?: return@flatMap Observable.error(Exception("chapter_id bulunamadı"))
                val loadTime = LOAD_TIME_REGEX.find(documentHtml)?.groupValues?.get(1)
                    ?: return@flatMap Observable.error(Exception("load_time bulunamadı"))
                val pageToken = PAGE_TOKEN_REGEX.find(documentHtml)?.groupValues?.get(1)
                    ?: return@flatMap Observable.error(Exception("page_token bulunamadı"))
                val nonce = NONCE_REGEX.find(documentHtml)?.groupValues?.get(1)
                    ?: return@flatMap Observable.error(Exception("nonce bulunamadı"))

                val form = FormBody.Builder()
                    .add("action", "holy_get_chapter_images")
                    .add("nonce", nonce)
                    .add("chapter_id", chapterId)
                    .add("load_time", loadTime)
                    .add("page_token", pageToken)
                    .build()

                val ajaxHeaders = headersBuilder()
                    .set("Referer", response.request.url.toString())
                    .set("X-Requested-With", "XMLHttpRequest")
                    .build()

                val ajaxRequest = POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, form)

                client.newCall(ajaxRequest).asObservableSuccess().map { ajaxResponse ->
                    val dto = ajaxResponse.parseAs<PagesResponse>()
                    dto.urls.mapIndexed { i, url ->
                        Page(i, url = chapter.url, imageUrl = url)
                    }
                }
            }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
            .set("Referer", baseUrl + page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
        TypeFilter(getTypeList()),
        StatusFilter(getStatusList()),
    )

    // ============================= Utilities =============================

    private fun parseRelativeDate(dateStr: String): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val lowerDate = dateStr.lowercase()
        return when {
            lowerDate.contains("yeni") || lowerDate.contains("bugün") ||
                lowerDate.contains("saat") || lowerDate.contains("dakika") -> Calendar.getInstance().timeInMillis
            lowerDate.contains("dün") -> now.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            lowerDate.contains("gün") -> {
                val days = lowerDate.substringBefore(" ").toIntOrNull() ?: 0
                now.apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
            }
            lowerDate.contains("hafta") -> {
                val weeks = lowerDate.substringBefore(" ").toIntOrNull() ?: 0
                now.apply { add(Calendar.DAY_OF_YEAR, -(weeks * 7)) }.timeInMillis
            }
            lowerDate.contains("ay") -> {
                val months = lowerDate.substringBefore(" ").toIntOrNull() ?: 0
                now.apply { add(Calendar.MONTH, -months) }.timeInMillis
            }
            lowerDate.contains("yıl") -> {
                val years = lowerDate.substringBefore(" ").toIntOrNull() ?: 0
                now.apply { add(Calendar.YEAR, -years) }.timeInMillis
            }
            else -> 0L
        }
    }

    private fun loginInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.pathSegments.lastOrNull() == "giris") {
            return chain.proceed(request)
        }

        val username = preferences.getString(PREF_USERNAME, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""

        if (username.isNotBlank() && password.isNotBlank()) {
            val cookies = network.client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            val isLoggedIn = cookies.any { it.name.startsWith("wordpress_logged_in_") }

            if (!isLoggedIn) {
                loginMutex.withLock {
                    val cookiesInside = network.client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                    val isLoggedInInside = cookiesInside.any { it.name.startsWith("wordpress_logged_in_") }

                    if (!isLoggedInInside) {
                        val form = FormBody.Builder()
                            .add("log", username)
                            .add("pwd", password)
                            .add("submit_custom_login", "")
                            .add("rememberme", "forever")
                            .build()

                        val loginRequest = POST("$baseUrl/giris/", headers, form)
                        chain.proceed(loginRequest).close()
                    }
                }
            }
        }

        return chain.proceed(request)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val usernamePref = EditTextPreference(screen.context).apply {
            key = PREF_USERNAME
            title = "Kullanıcı Adı / E-posta"
            summary = "Gizli kapakları ve bölümleri görebilmek için gereklidir. Hesabınız yoksa site üzerinden oluşturabilirsiniz."
        }
        val passwordPref = EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Şifre"
            summary = "Hesabınızın şifresi."
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
        screen.addPreference(usernamePref)
        screen.addPreference(passwordPref)
    }

    companion object {
        private const val PREF_USERNAME = "pref_username"
        private const val PREF_PASSWORD = "pref_password"

        private val CHAPTER_ID_REGEX = Regex(""""chapter_id"\s*:\s*(\d+)""")
        private val LOAD_TIME_REGEX = Regex(""""load_time"\s*:\s*(\d+)""")
        private val PAGE_TOKEN_REGEX = Regex(""""page_token"\s*:\s*"([^"]+)"""")
        private val NONCE_REGEX = Regex(""""nonce"\s*:\s*"([^"]+)"""")
    }
}
