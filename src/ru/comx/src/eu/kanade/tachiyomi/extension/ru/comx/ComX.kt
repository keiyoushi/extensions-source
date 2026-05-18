package eu.kanade.tachiyomi.extension.ru.comx

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ComX :
    HttpSource(),
    ConfigurableSource {

    override val id = 1114173092141608635

    override val name = "Com-X"

    private val preferences: SharedPreferences = getPreferences()

    init {
        preferences.getString(DOMAIN_PREF, DOMAIN_DEFAULT)?.let { domain ->
            if (!domain.matches(URL_REGEX)) {
                preferences.edit()
                    .putString(DOMAIN_PREF, DOMAIN_DEFAULT)
                    .apply()
            }
        }
        preferences.getString(DEFAULT_DOMAIN_PREF, null).let { prefDefaultDomain ->
            if (prefDefaultDomain != DOMAIN_DEFAULT) {
                preferences.edit()
                    .putString(DOMAIN_PREF, DOMAIN_DEFAULT)
                    .putString(DEFAULT_DOMAIN_PREF, DOMAIN_DEFAULT)
                    .apply()
            }
        }
    }

    override val baseUrl = preferences.getString(DOMAIN_PREF, DOMAIN_DEFAULT)!!

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(3)
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            val finalRequest = if (url.contains("/reader/")) {
                val adultValue = url.substringAfter("/reader/").substringBefore("/")
                val originalCookie = request.header("Cookie") ?: ""
                val newCookie = if (originalCookie.isNotEmpty()) "$originalCookie; adult=$adultValue" else "adult=$adultValue"

                request.newBuilder()
                    .header("Cookie", newCookie)
                    .build()
            } else {
                request
            }

            var response = chain.proceed(finalRequest)
            if (response.code == 404 && response.request.url.encodedPath == "/_c") {
                response = solveGuardChallenge(chain, response, finalRequest)
            }

            val imgPreloadHost = baseUrl.replace(Regex("^https?://"), "img.")
            if (response.code == 403 &&
                request.url.toString().contains("/comix/") &&
                request.url.host != imgPreloadHost
            ) {
                val newUrl = request.url.newBuilder()
                    .host(imgPreloadHost)
                    .build()

                val newRequest = request.newBuilder().url(newUrl).headers(headers).build()
                response.close()
                response = chain.proceed(newRequest)
            }
            response
        }
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList())

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.short").map { element ->
            SManga.create().apply {
                element.selectFirst("img")?.let { img ->
                    val src = img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src")
                    thumbnail_url = if (src.contains("://")) src else baseUrl + src
                }
                element.selectFirst(".readed__title a")?.let {
                    setUrlWithoutDomain(it.attr("abs:href"))
                    title = it.text().replace(" / ", " | ").substringAfterLast(" | ").trim()
                }
            }
        }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul#content-load li.latest").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.replace("mini/mini", "mini/mid")
                element.selectFirst(".latest__title a")?.let {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = it.text().replace(" / ", " | ").substringAfterLast(" | ").trim()
                }
            }
        }
        val hasNextPage = document.selectFirst("div.pagination__btn-loader a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addPathSegment(query)
                addPathSegments("page/$page")
            }.build()
            return GET(url, headers)
        }

        val mutableGenre = mutableListOf<String>()
        val mutableType = mutableListOf<String>()
        val mutableAge = mutableListOf<String>()
        val sectionPub = mutableListOf<String>()
        var orderBy = "rating"
        var ascEnd = "desc"

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    orderBy = arrayOf("date", "rating", "news_read", "comm_num", "title")[filter.state!!.index]
                    ascEnd = if (filter.state!!.ascending) "asc" else "desc"
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state) {
                        mutableAge += age.id
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state) {
                        mutableGenre += genre.id
                    }
                }
                is TypeList -> filter.state.forEach { type ->
                    if (type.state) {
                        mutableType += type.id
                    }
                }
                is PubList -> filter.state.forEach { publisher ->
                    if (publisher.state) {
                        sectionPub += publisher.id
                    }
                }
                else -> {}
            }
        }
        val pageParameter = if (page > 1) "page/$page/" else ""
        return POST(
            "$baseUrl/ComicList/p.cat=${sectionPub.joinToString(",")}/g=${mutableGenre.joinToString(",")}/t=${mutableType.joinToString(",")}/adult=${mutableAge.joinToString(",")}/$pageParameter",
            body = FormBody.Builder()
                .add("dlenewssortby", orderBy)
                .add("dledirection", ascEnd)
                .add("set_new_sort", "dle_sort_xfilter")
                .add("set_direction_sort", "dle_direction_xfilter")
                .build(),
            headers = headers,
        )
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.page__grid") ?: return SManga.create()

        val ratingValue = (infoElement.selectFirst(".page__activity-votes")?.ownText()?.trim()?.toFloatOrNull() ?: 0f) * 2
        val ratingVotes = infoElement.selectFirst(".page__activity-votes span > span")?.text()?.trim() ?: "0"
        val ratingStar = when {
            ratingValue > 9.5 -> "★★★★★"
            ratingValue > 8.5 -> "★★★★✬"
            ratingValue > 7.5 -> "★★★★☆"
            ratingValue > 6.5 -> "★★★✬☆"
            ratingValue > 5.5 -> "★★★☆☆"
            ratingValue > 4.5 -> "★★✬☆☆"
            ratingValue > 3.5 -> "★★☆☆☆"
            ratingValue > 2.5 -> "★✬☆☆☆"
            ratingValue > 1.5 -> "★☆☆☆☆"
            ratingValue > 0.5 -> "✬☆☆☆☆"
            else -> "☆☆☆☆☆"
        }

        val rawCategory = document.select(".speedbar a").last()?.text()?.trim() ?: ""
        val category = when (rawCategory.lowercase()) {
            "manga" -> "Манга"
            "manhwa" -> "Манхва"
            "manhua" -> "Маньхуа"
            else -> "Комикс"
        }
        val rawAgeStop = if (document.html().contains("ВНИМАНИЕ! 18+")) "18+" else ""

        return SManga.create().apply {
            title = infoElement.selectFirst(".page__header h1")?.text()?.trim() ?: ""
            author = infoElement.select("li:contains(Издатель)").text().substringAfter("Издатель:").trim()
            genre = listOf(category, rawAgeStop).plus(infoElement.select(".page__tags a").map { it.text() })
                .filter { it.isNotBlank() }
                .joinToString(", ")
            status = parseStatus(infoElement.select(".page__list li:contains(Статус)").text())

            description = buildString {
                infoElement.selectFirst(".page__title-original")?.text()?.takeIf { it.isNotBlank() }?.let {
                    append(it).append("\n")
                }
                if (document.select(".page__list li:contains(Тип выпуска)").text().contains("!!! События в комиксах - ХРОНОЛОГИЯ !!!")) {
                    append("Cобытие в комиксах - ХРОНОЛОГИЯ\n")
                }
                append(ratingStar).append(" ").append(ratingValue).append(" (голосов: ").append(ratingVotes).append(")\n")

                val textHtml = infoElement.selectFirst(".page__text")?.html() ?: ""
                val text = Jsoup.parseBodyFragment(textHtml).select("body:not(:has(p)), p, br")
                    .prepend("\\n").text().replace("\\n", "\n").replace("\n ", "\n")
                append(text)
            }.trim()

            val img = infoElement.selectFirst(".img-wide img")
            thumbnail_url = img?.absUrl("data-src")?.takeIf { it.isNotEmpty() } ?: img?.absUrl("src")
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Продолжается") || element.contains(" из ") || element.contains("Онгоинг") -> SManga.ONGOING
        element.contains("Заверш") || element.contains("Лимитка") || element.contains("Ван шот") || element.contains("Графический роман") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun solveGuardChallenge(chain: Interceptor.Chain, challenge: Response, original: Request): Response {
        val token = TOKEN_REGEX.find(challenge.peekBody(4096).string())?.groupValues?.get(1)
            ?: throw IOException("Antibot challenge failed: token not found")
        challenge.close()

        var nonce = 0L
        val md = MessageDigest.getInstance("SHA-256")
        val start = System.currentTimeMillis()
        while (md.digest("$token:$nonce".toByteArray())[0] != 0.toByte()) {
            nonce++
            if (nonce > 1_000_000L) throw IOException("Antibot challenge failed: PoW exhausted")
        }
        val workTime = (System.currentTimeMillis() - start).coerceAtLeast(120)

        val verifyBody = FormBody.Builder()
            .add("token", token).add("mode", "modern")
            .add("workTime", workTime.toString()).add("iterations", (nonce + 1).toString())
            .add("webdriver", "0").add("touch", "1")
            .add("screen_w", "390").add("screen_h", "844").add("screen_cd", "24")
            .add("wgv", "Apple Inc.").add("wgr", "Apple GPU")
            .add("tz", "-180").add("dpr", "3").add("cdp", "0").add("cdpf", "")
            .build()

        val verifyReq = Request.Builder()
            .url("$baseUrl/_v")
            .post(verifyBody)
            .headers(headers)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", challenge.request.url.toString())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()
        chain.proceed(verifyReq).close()

        return chain.proceed(original)
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val extraStep = 0.001f
        val document = response.asJsoup()

        val dataStr = document.outerHtml()
            .substringAfter("window.__DATA__ = ", "")
            .substringBefore("</script>")
            .substringBeforeLast(";")

        if (dataStr.isEmpty()) return emptyList()

        val data = try {
            dataStr.parseAs<JsonObject>()
        } catch (e: Exception) {
            return emptyList()
        }

        val chaptersList = data["chapters"]?.jsonArray ?: return emptyList()
        val newsId = data["news_id"]?.jsonPrimitive?.content ?: return emptyList()

        val isEvent = document
            .select(".page__list li:contains(Тип выпуска)")
            .text()
            .contains("!!! События в комиксах - ХРОНОЛОГИЯ !!!")

        var currentBase = 0f
        var subIndex = 0

        val pendingExtras = mutableListOf<SChapter>()
        val result = mutableListOf<SChapter>()

        chaptersList.forEach { element ->
            val obj = element.jsonObject
            val chapter = SChapter.create()

            val title = obj["title"]?.jsonPrimitive?.content ?: ""
            val posi = obj["posi"]?.jsonPrimitive?.float ?: 0f

            chapter.name = title
            chapter.date_upload = dateFormat.tryParse(obj["date"]?.jsonPrimitive?.content)

            val parsedBase = parseBaseChapterNumber(title)
            val isExtra = isExtraChapter(title)

            when {
                parsedBase != null -> {
                    currentBase = parsedBase
                    subIndex = 0
                    if (pendingExtras.isNotEmpty()) {
                        pendingExtras.reversed().forEach {
                            subIndex++
                            it.chapter_number = currentBase + subIndex * extraStep
                        }
                        pendingExtras.clear()
                    }
                    chapter.chapter_number = parsedBase
                }
                isExtra -> {
                    pendingExtras += chapter
                }
                else -> {
                    currentBase = posi
                    subIndex = 0
                    if (pendingExtras.isNotEmpty()) {
                        pendingExtras.reversed().forEach {
                            subIndex++
                            it.chapter_number = currentBase + subIndex * extraStep
                        }
                        pendingExtras.clear()
                    }
                    chapter.chapter_number = posi
                }
            }
            if (isEvent && chapter.chapter_number > 0f) {
                chapter.name = "${chapter.chapter_number.toInt()} ${chapter.name}"
            }
            chapter.setUrlWithoutDomain("/reader/$newsId/${obj["id"]!!.jsonPrimitive.content}")

            result += chapter
        }

        if (pendingExtras.isNotEmpty()) {
            subIndex = 0
            pendingExtras.reversed().forEach {
                subIndex++
                it.chapter_number = currentBase + subIndex * extraStep
            }
        }

        return result
    }

    private fun parseBaseChapterNumber(title: String): Float? {
        val dashIndex = title.indexOf('-')
        if (dashIndex == -1) return null

        val afterDash = title.substring(dashIndex + 1).trimStart()

        val end = afterDash.indexOf(' ').let {
            if (it == -1) afterDash.length else it
        }

        return afterDash.take(end).toFloatOrNull()
    }

    private fun isExtraChapter(title: String): Boolean {
        val lower = title.lowercase()
        return "экстра" in lower ||
            "extra" in lower ||
            "special" in lower ||
            "bonus" in lower
    }

    // Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter)).asObservable().map { response ->
        response.use {
            if (!it.isSuccessful) {
                if (it.code == 404 && it.peekBody(2048).string().contains("Выпуск был удален по требованию правообладателя")) {
                    throw Exception("Лицензировано. Возможно может помочь авторизация через WebView")
                } else {
                    throw Exception("HTTP error ${it.code}")
                }
            }
            pageListParse(it)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        if (html.contains("adult__header")) {
            throw Exception("Комикс 18+ (что-то сломалось)")
        }

        val imageUrl = preferences.getString(FORCE_IMG_DOMAIN_PREF, null)?.takeIf { it.isNotBlank() }
            ?: IMG_DOMAIN_REGEX.find(html)?.groupValues?.get(1)?.let { "https://$it" }

        if (imageUrl.isNullOrBlank()) {
            throw Exception("Не удалось определить домен картинок. Попробуйте задать вручную в настройках")
        }

        val beginTag = "\"images\":["
        val beginIndex = html.indexOf(beginTag)
        if (beginIndex == -1) return emptyList()
        val endIndex = html.indexOf("]", beginIndex)

        val urls = html.substring(beginIndex + beginTag.length, endIndex)
            .split(',').map {
                val img = it.replace("\\", "").replace("\"", "")
                "$imageUrl/comix/$img"
            }

        return urls.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    // Filters
    override fun getFilterList() = FilterList(
        OrderBy(),
        PubList(pubList),
        GenreList(genreList),
        TypeList(typeList),
        AgeList(ageList),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = "Домен"
            summary = "$baseUrl\n\nПо умолчанию: $DOMAIN_DEFAULT"
            setDefaultValue(DOMAIN_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                if (!newValue.toString().matches(URL_REGEX)) {
                    val warning = "Домен должен содаржать https:// или http://"
                    Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = FORCE_IMG_DOMAIN_PREF
            title = "Домен картинок"
            summary = "Если изображения не грузяться очистите «Кэш приложения» и всевозможные данные в настройках приложения  (Настройки -> Дополнительно) \nи перезапустите приложение с полной остановкой" +
                "\n\nНастройка переопределяет домен картинок." +
                "\nПо умолчанию домент картинок берётся автоматически." +
                "\nЧтобы узнать домен изображения откройте главу в браузере и после долгим тапом откройте изображение в новом окне."
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private val dateFormat by lazy { SimpleDateFormat("dd.MM.yyyy", Locale.US) }

        private const val DOMAIN_DEFAULT = "https://ru.com-x.life"

        private const val DEFAULT_DOMAIN_PREF = "DEFAULT_DOMAIN_PREF"
        private const val DOMAIN_PREF = "DOMAIN_PREF"
        private const val FORCE_IMG_DOMAIN_PREF = "FORCE_IMG_DOMAIN_PREF"

        private val URL_REGEX = Regex("^https?://.+")
        private val IMG_DOMAIN_REGEX = "\"host\":\"(.+?)\"".toRegex()
        private val TOKEN_REGEX = """token:\s*"([^"]+)"""".toRegex()
    }
}
