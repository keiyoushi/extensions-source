package eu.kanade.tachiyomi.extension.tr.holyscans

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Calendar
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Source
abstract class HolyScans :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    private val loginMutex = ReentrantLock()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::loginInterceptor)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val form = FormBody.Builder()
            .add("action", "filter_manga_archive")
            .add("paged", page.toString())
            .build()

        val referer = "$baseUrl/manga/?m_orderby=views"
        val popularHeaders = headers.newBuilder().set("Referer", referer).build()
        return parseAjaxMangaList(client.post("$baseUrl/wp-admin/admin-ajax.php", popularHeaders, form))
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val form = FormBody.Builder()
                .add("action", "holy_live_search")
                .add("keyword", query)
                .build()

            val dto = client.post("$baseUrl/wp-admin/admin-ajax.php", body = form).parseAs<LiveSearchResponse>()
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

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
        val types = filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()

        val form = FormBody.Builder()
            .add("action", "filter_manga_archive")
            .add("paged", page.toString())

        genres.forEach { form.add("genres[]", it) }
        types.forEach { form.add("types[]", it) }
        statuses.forEach { form.add("statuses[]", it) }

        return parseAjaxMangaList(client.post("$baseUrl/wp-admin/admin-ajax.php", body = form.build()))
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        manga.apply {
            title = document.selectFirst("h1.hs-title")?.text() ?: throw Exception("Manga başlığı bulunamadı")
            // the cover <img> is a locked placeholder for guests, og:image always has the real cover
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            author = document.selectFirst(".hs-info-row:contains(Yazar) .val")?.text()
            artist = document.selectFirst(".hs-info-row:contains(Çizer) .val")?.text()
            genre = document.select(".hs-genres a").joinToString { it.text() }
            status = when (document.selectFirst(".hs-info-row:contains(Durum) .hs-pill")?.text()?.lowercase()) {
                "devam ediyor" -> SManga.ONGOING
                "tamamlandı", "final" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = document.selectFirst(".hs-summary-content")?.text()
        }

        val chapterList = document.select(".manga-chapter-list-wrap .ch-list-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                val chapterName = element.selectFirst(".ch-title")?.ownText() ?: throw Exception("Bölüm adı bulunamadı")
                name = if (element.hasClass("ch-locked")) "🔒 $chapterName" else chapterName
                date_upload = parseRelativeDate(element.selectFirst(".ch-date")?.text() ?: "")
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterUrl, documentHtml) = client.get(getChapterUrl(chapter)).use {
            it.request.url.toString() to it.body.string()
        }

        val chapterId = CHAPTER_ID_REGEX.find(documentHtml)?.groupValues?.get(1)
            ?: throw Exception("Bu bölüm kilitli (VIP/coin), WebView üzerinden açın")
        val loadTime = LOAD_TIME_REGEX.find(documentHtml)?.groupValues?.get(1)
            ?: throw Exception("load_time bulunamadı")
        val pageToken = PAGE_TOKEN_REGEX.find(documentHtml)?.groupValues?.get(1)
            ?: throw Exception("page_token bulunamadı")
        val nonce = NONCE_REGEX.find(documentHtml)?.groupValues?.get(1)
            ?: throw Exception("nonce bulunamadı")

        val form = FormBody.Builder()
            .add("action", "holy_get_chapter_images")
            .add("nonce", nonce)
            .add("chapter_id", chapterId)
            .add("load_time", loadTime)
            .add("page_token", pageToken)
            .build()

        val ajaxHeaders = headers.newBuilder()
            .set("Referer", chapterUrl)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val dto = client.post("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, form).parseAs<PagesResponse>()
        return dto.urls.mapIndexed { i, url ->
            Page(i, url = chapter.url, imageUrl = url)
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
        .header("Referer", baseUrl + page.url)
        .build()

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
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
