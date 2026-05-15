package eu.kanade.tachiyomi.extension.ru.mangahub

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class Mangahub :
    HttpSource(),
    ConfigurableSource {

    override val name = "Mangahub"

    override val lang = "ru"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy {
        getString(DOMAIN_OVERRIDE, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != DOMAIN_DEFAULT) {
                edit()
                    .putString(DOMAIN_PREF, DOMAIN_DEFAULT)
                    .putString(DOMAIN_OVERRIDE, DOMAIN_DEFAULT)
                    .apply()
            }
        }
    }

    override val baseUrl = preferences.getString(DOMAIN_PREF, DOMAIN_DEFAULT)!!

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::confirmAgeInterceptor)
        .rateLimit(2)
        .build()

    private fun confirmAgeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.method != "GET" ||
            response.header("Content-Type")?.contains("text/html") != true
        ) {
            return response
        }

        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            request.url.toString(),
        )

        val formElement = document.selectFirst("#confirm_age__token")
            ?: return response

        val formBody = FormBody.Builder()
            .addEncoded(formElement.attr("name"), formElement.attr("value"))
            .build()

        val confirmAgeRequest = request.newBuilder()
            .method("POST", formBody)
            .build()

        response.close()

        return chain.proceed(confirmAgeRequest)
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page > 1) "?page=$page" else ""
        return GET("$baseUrl/explore/sort-is-rating$pageStr", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.item-grid").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img.item-grid-image")?.absUrl("src")
                title = element.selectFirst("a.fw-medium")!!.text()
                setUrlWithoutDomain(element.selectFirst("a.fw-medium")!!.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst(".page-link:contains(→)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page > 1) "?page=$page" else ""
        return GET("$baseUrl/explore/sort-is-update$pageStr", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/title".toHttpUrl().newBuilder().apply {
            addQueryParameter("query", query)
            if (page > 1) addQueryParameter("page", page.toString())
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "/chapters", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val authorElement = document.selectFirst(".attr-name:contains(Автор) + .attr-value a")
            if (authorElement != null) {
                author = authorElement.text()
            } else {
                author = document.selectFirst(".attr-name:contains(Сценарист) + .attr-value a")?.text()
                artist = document.selectFirst(".attr-name:contains(Художник) + .attr-value a")?.text()
            }
            genre = document.select(".tags a").joinToString { it.text() }
            description = document.selectFirst(".markdown-style.text-expandable-content")?.text()
            val statusElement = document.selectFirst(".attr-name:contains(Томов) + .attr-value")?.text()
            status = when {
                statusElement?.contains("продолжается") == true -> SManga.ONGOING
                statusElement?.contains("приостановлен") == true -> SManga.ON_HIATUS
                statusElement?.contains("завершен") == true || statusElement?.contains("выпуск прекращён") == true ->
                    if (document.selectFirst(".attr-name:contains(Перевод) + .attr-value")?.text()?.contains("Завершен") == true) {
                        SManga.COMPLETED
                    } else {
                        SManga.PUBLISHING_FINISHED
                    }
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst("img.cover-detail")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.py-2.px-3").map { element ->
            val urlElement = element.selectFirst("div.align-items-center > a")!!
            SChapter.create().apply {
                name = urlElement.text()
                date_upload = dateFormat.tryParse(element.selectFirst("div.text-muted")?.text())
                setUrlWithoutDomain(urlElement.absUrl("href"))
            }
        }
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        chapterRegex.find(chapter.name)?.let {
            chapter.chapter_number = it.groups[2]?.value?.toFloatOrNull() ?: -1f
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("img.reader-viewer-img")
        return images.mapIndexed { i, img ->
            val url = img.attr("data-src").let { if (it.startsWith("//")) "https:$it" else it }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = "Домен"
            summary = "$baseUrl\n\nПо умолчанию: $DOMAIN_DEFAULT"
            setDefaultValue(DOMAIN_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                val url = newValue.toString()
                if (!url.matches(URL_REGEX)) {
                    Toast.makeText(screen.context, DOMAIN_MISMATCH_1, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                if (url.endsWith("/")) {
                    Toast.makeText(screen.context, DOMAIN_MISMATCH_2, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                if (url.length !in 12..255) {
                    Toast.makeText(screen.context, DOMAIN_MISMATCH_3, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                Toast.makeText(screen.context, DOMAIN_RESTART_MESSAGE, Toast.LENGTH_LONG).show()
                this.summary = "$newValue\n\nПо умолчанию: $DOMAIN_DEFAULT"
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private val chapterRegex = Regex("""(Глава\s)((\d|\.)+)""")
        private val dateFormat by lazy {
            SimpleDateFormat("dd.MM.yyyy", Locale.US)
        }
        private const val DOMAIN_DEFAULT = "https://mangahub.ru"
        private const val DOMAIN_PREF = "defaultBaseUrl"
        private const val DOMAIN_OVERRIDE = "overrideBaseUrl"
        private const val DOMAIN_RESTART_MESSAGE = "Для смены домена необходимо перезапустить приложение с полной остановкой."
        private const val DOMAIN_MISMATCH_1 = "Домен должен начинаться с https:// или http://."
        private const val DOMAIN_MISMATCH_2 = "Домен не должен заканчиваться символом /."
        private const val DOMAIN_MISMATCH_3 = "Домен не может быть меньше 12 символов. И не больше 255."
        private val URL_REGEX = Regex("^https?://.+")
    }
}
