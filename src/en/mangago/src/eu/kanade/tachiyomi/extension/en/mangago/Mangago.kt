package eu.kanade.tachiyomi.extension.en.mangago

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.network.rateLimit
import keiyoushi.utils.decodeHex
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Mangago :
    HttpSource(),
    ConfigurableSource {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private val domain = "mangago.me"

    // Chapter reader mirror; "/chapter/..." paths 404 on the main domain.
    // Chapter lists are also fetched from here: the main domain randomly
    // alternates between two chapter URL formats with unrelated ids, while
    // the mirror consistently serves "/chapter/<mangaId>/<chapterId>/".
    private val readerDomain = "www.mangago.zone"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            val fragment = request.url.fragment
                ?.takeIf { it.contains("desckey=") }
                ?: return@addInterceptor response

            // desckey=...&cols=...
            val key = fragment.substringAfter("desckey=").substringBefore("&")
            val cols = fragment.substringAfter("&cols=").toIntOrNull() ?: return@addInterceptor response

            // Wrap in `use` to automatically close the stream
            val body = response.body.byteStream().use { stream ->
                unscrambleImage(stream, key, cols)
            }

            return@addInterceptor response.newBuilder()
                .body(body)
                .build()
        }
        .addNetworkInterceptor(CookieInterceptor(domain, "_m_superu" to "1"))
        .rateLimit(1) { it.host == baseUrlHost || it.host == readerDomain }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        preferences.getString(PREF_KEY_CUSTOM_UA, null)?.takeIf { it.isNotBlank() }?.also {
            set("User-Agent", it)
        }
        add("Referer", "$baseUrl/")
    }

    // ============================== Popular ==============================

    private val genreListingSelector = ".updatesli"
    private val genreListingNextPageSelector = ".current+li > a"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/genre/all/$page/?f=1&o=1&sortby=view&e=", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(genreListingSelector)
            .mapNotNull { mangaFromElement(it) }
        val hasNextPage = document.selectFirst(genreListingNextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/all/$page/?f=1&o=1&sortby=update_date&e=", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/r/l_search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("page", page.toString())
                .build().toString()
        } else {
            "$baseUrl/genre/".toHttpUrl().newBuilder().apply {
                val genres = mutableListOf<String>()
                val genresEx = mutableListOf<String>()

                filters.ifEmpty { getFilterList() }.forEach {
                    when (it) {
                        is UriFilter -> it.addToUrl(this)
                        is GenreFilterGroup -> it.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_EXCLUDE -> genresEx.add(genre.name)
                                Filter.TriState.STATE_INCLUDE -> genres.add(genre.name)
                                else -> {}
                            }
                        }
                        else -> {}
                    }
                }

                if (genres.isEmpty()) {
                    addPathSegment("all")
                } else {
                    addPathSegment(genres.joinToString(","))
                }
                addPathSegment(page.toString())

                addQueryParameter("e", genresEx.joinToString(","))
            }.build().toString()
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("$genreListingSelector, .pic_list > li")
            .mapNotNull { mangaFromElement(it) }
        val hasNextPage = document.selectFirst(genreListingNextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            document.selectFirst(".w-title h1")?.text()?.let {
                title = if (isRemoveTitleVersion()) {
                    it.replace(titleRegex, "")
                } else {
                    it
                }
            }

            document.getElementById("information")?.let { info: Element ->
                thumbnail_url = info.selectFirst("img")?.attr("abs:src")
                description = info.selectFirst(".manga_summary")?.let { summary: Element ->
                    summary.selectFirst("font")?.remove()
                    summary.text()
                }?.takeIf { !it.equals("not found...", ignoreCase = true) }

                info.select(".manga_info li, .manga_right tr").forEach { el ->
                    when (el.selectFirst("b, label")?.text()?.lowercase()) {
                        "alternative:" -> {
                            val labelText = el.selectFirst("b, label")?.text().orEmpty()
                            val raw = el.text().removePrefix(labelText).trim()
                            val altNames = parseAltNames(raw)

                            if (altNames.isNotEmpty()) {
                                description = buildString {
                                    append(description.orEmpty())
                                    if (isNotEmpty()) append("\n\n")
                                    append(ALT_NAME_PREFIX)
                                    append("\n")
                                    altNames.joinTo(this, "\n") { "- $it" }
                                }
                            }
                        }

                        "status:" -> status = when (el.selectFirst("span")?.text()?.lowercase()) {
                            "ongoing" -> SManga.ONGOING
                            "completed" -> SManga.COMPLETED
                            else -> SManga.UNKNOWN
                        }

                        "author(s):", "author:" -> author = el.select("a").joinToString { it.text() }

                        "genre(s):" -> genre = el.select("a").joinToString { it.text() }
                    }
                }
            }
        }
    }

    private fun parseAltNames(raw: String): List<String> {
        val separator = if (ALT_NAME_SLASH_SEMICOLON_REGEX.containsMatchIn(raw)) {
            ALT_NAME_SLASH_SEMICOLON_REGEX
        } else {
            ALT_NAME_COMMA_REGEX
        }

        return raw.split(separator)
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.equals("None", ignoreCase = true)
            }
    }

    private fun mangaFromElement(element: Element): SManga? = SManga.create().apply {
        val linkElement = element.selectFirst(".thm-effect") ?: return null

        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = linkElement.attr("title").takeIf { it.isNotBlank() } ?: return null
        thumbnail_url = linkElement.selectFirst("img")?.imgAttr()
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("https://$readerDomain${manga.url}", headers)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .onErrorResumeNext {
            client.newCall(GET("$baseUrl${manga.url}", headers)).asObservableSuccess()
        }
        .map(::chapterListParse)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("table#chapter_table > tbody > tr, table.uk-table > tbody > tr")
            .map { element ->
                SChapter.create().apply {
                    val link = element.select("a.chico")
                    val urlOriginal = link.attr("abs:href")
                    val httpUrl = urlOriginal.toHttpUrl()

                    // Reader links rotate between mirror hosts but keep a
                    // stable path. Store only the path so rotated links don't
                    // register as new chapters (resetting read state); the
                    // host is restored by pageListRequest/getChapterUrl.
                    // Truly external links stay absolute.
                    when {
                        httpUrl.pathSegments.firstOrNull() == "chapter" -> url = httpUrl.encodedPath
                        httpUrl.host.endsWith(domain) -> setUrlWithoutDomain(urlOriginal)
                        else -> url = urlOriginal
                    }

                    name = link.text()
                    date_upload = dateFormat.tryParse(element.select("td:last-child").text())
                    scanlator = element.selectFirst("td.no a, td.uk-table-shrink a")?.text()

                    if (scanlator.isNullOrEmpty()) {
                        scanlator = "Unknown"
                    }
                }
            }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val availableImages = getChapterImageUrls(document)

        if (availableImages.none { it.isBlank() }) {
            return availableImages.mapIndexed { idx, img ->
                Page(idx, imageUrl = img)
            }
        }

        val totalPages = document.selectFirst("script:containsData(total_pages)")
            ?.data()
            ?.let { Regex("""total_pages\s*=\s*(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: throw Exception("Total page count not found")

        val urlTemplate = document.selectFirst("input#curl")!!
            .attr("value").trim()
            .removePrefix("/")
            .also {
                if (!it.contains("{page}")) {
                    throw Exception("No replaceable string in url template")
                }
            }

        val prefix = with(document.location().toHttpUrl()) {
            val urlTemplateSegment = urlTemplate.split("/")[0]
            if (
                host.endsWith(domain) &&
                pathSegments.size > 3 &&
                pathSegments[0] == "read-manga" &&
                pathSegments[2] == urlTemplateSegment
            ) {
                val slug = pathSegments[1]
                "$baseUrl/read-manga/$slug"
            } else if (
                !host.endsWith(domain) &&
                pathSegments[0] == urlTemplateSegment
            ) {
                "https://$host"
            } else {
                throw Exception("Unexpected Url structure")
            }
        }

        val pages = (1..totalPages).map { page ->
            val url = prefix.toHttpUrl().newBuilder()
                .addEncodedPathSegments(urlTemplate.replace("{page}", page.toString()))
                .fragment(page.toString())
                .build()
                .toString()

            Page(page, url)
        }

        pages.onEachIndexed { index, page ->
            availableImages[index].also {
                if (it.isNotBlank()) {
                    page.imageUrl = it
                }
            }
        }

        return pages
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Absolute URLs may still be present in the library from older
        // versions of the extension.
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        // Reader paths only resolve on the mirror domains, not on baseUrl.
        if (chapter.url.startsWith("/chapter/")) {
            return GET("https://$readerDomain${chapter.url}", headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith("http")) {
            return chapter.url
        }
        if (chapter.url.startsWith("/chapter/")) {
            return "https://$readerDomain${chapter.url}"
        }
        return super.getChapterUrl(chapter)
    }

    override fun imageUrlParse(response: Response): String {
        val index = response.request.url.fragment!!.toInt() - 1
        val document = response.asJsoup()
        val availableImages = getChapterImageUrls(document)

        return availableImages.getOrNull(index)
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("Unable to find image for page ${index + 1}")
    }

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!.toHttpUrl()

        return if (url.host.contains("_")) {
            // workaround for "android internal error" caused by _ in domain
            val imageUrl = url.newBuilder()
                .scheme("http")
                .build()

            GET(imageUrl, headers)
        } else {
            GET(url, headers)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Ignored if using text search"),
        SortFilter(),
        StatusFilterGroup(),
        GenreFilterGroup(),
    )

    // ============================= Utilities =============================

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val document = response.asJsoup()
        return document.select("div.pic_list .updatesli, .also-like li")
            .mapNotNull { element ->
                element.selectFirst("a[title]")?.let { elm: Element ->
                    SManga.create().apply {
                        title = elm.ownText().takeIf { it.isNotEmpty() }
                            ?: elm.attr("title").takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        setUrlWithoutDomain(elm.absUrl("href"))
                        thumbnail_url = element.selectFirst("img")?.imgAttr()
                    }
                } ?: return@mapNotNull null
            }
    }

    private fun getChapterImageUrls(document: Document): List<String> {
        val images = document.selectFirst("script:containsData(imgsrcs)")
            ?.data()
            ?.let { imgSrcsRegex.find(it)?.groupValues?.get(1) }
            ?.let { Base64.decode(it, Base64.DEFAULT) }
            ?: throw Exception("could not find 'imgsrcs' script")

        val chapterJsUrl = document.selectFirst("script[src*=chapter.js]")!!.absUrl("src")
        val chapterJs = SoJsonV4Deobfuscator.decode(
            client.newCall(
                GET(chapterJsUrl, headers),
            ).execute().use { it.body.string() },
        )
        val key = findHexEncodedVariable(chapterJs, "key").decodeHex()
        val iv = findHexEncodedVariable(chapterJs, "iv").decodeHex()

        val cipher = Cipher.getInstance("AES/CBC/ZEROBYTEPADDING").apply {
            val secretKeySpec = SecretKeySpec(key, "AES")
            val ivParameterSpec = IvParameterSpec(iv)

            init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        }

        var imageList = cipher.doFinal(images).toString(Charsets.UTF_8)

        imageList = unscrambleImageList(imageList, chapterJs)

        val cols = colsRegex.find(chapterJs)?.groupValues?.get(1) ?: ""

        val imgKeys = chapterJs
            .substringAfter("var renImg = function(img,width,height,id){")
            .substringBefore("key = key.split(")
            .split("\n")
            .filter { jsFilters.all { filter -> !it.contains(filter) } }
            .joinToString("\n")
            .replace("img.src", "url")

        val resolvedUrls = QuickJs.create().use { qjs ->
            qjs.execute(replacePosBytecode)
            qjs.evaluate("function getDescramblingKey(url) { $imgKeys; return key; }")

            imageList.split(",").map {
                if (it.contains("cspiclink")) {
                    val descKey = qjs.evaluate("""getDescramblingKey("$it");""") as String
                    "$it#desckey=$descKey&cols=$cols"
                } else {
                    it
                }
            }
        }

        return resolvedUrls
    }

    private fun findHexEncodedVariable(input: String, variable: String): String {
        val regex = Regex("""var $variable\s*=\s*CryptoJS\.enc\.Hex\.parse\("([0-9a-zA-Z]+)"\)""")
        return regex.find(input)?.groupValues?.get(1) ?: ""
    }

    private fun String.unscramble(keys: List<Int>): String {
        var s = this
        keys.reversed().forEach {
            for (i in s.length - 1 downTo it) {
                if (i % 2 != 0) {
                    val temp = s[i - it]
                    s = s.replaceRange(i - it..i - it, s[i].toString())
                    s = s.replaceRange(i..i, temp.toString())
                }
            }
        }
        return s
    }

    private fun unscrambleImageList(imageList: String, js: String): String {
        var imgList = imageList
        try {
            val keyLocations = keyLocationRegex.findAll(js).map {
                it.groupValues[1].toInt()
            }.distinct()

            val unscrambleKey = keyLocations.map {
                imgList[it].toString().toInt()
            }.toList()

            keyLocations.forEachIndexed { idx, it ->
                imgList = imgList.removeRange(it - idx..it - idx)
            }

            imgList = imgList.unscramble(unscrambleKey)
        } catch (_: NumberFormatException) {
            // Only call where it should throw is imageList[it].toString().toInt().
            // This usually means that the list is already unscrambled.
        }
        return imgList
    }

    private fun unscrambleImage(image: InputStream, key: String, cols: Int): ResponseBody {
        val bitmap = BitmapFactory.decodeStream(image)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val unitWidth = bitmap.width / cols
        val unitHeight = bitmap.height / cols
        val keyArray = key.split("a")

        for (idx in 0 until cols * cols) {
            val keyval = keyArray[idx].ifEmpty { "0" }.toInt()

            val heightY = keyval.floorDiv(cols)
            val dy = heightY * unitHeight
            val dx = (keyval - heightY * cols) * unitWidth

            val widthY = idx.floorDiv(cols)
            val sy = widthY * unitHeight
            val sx = (idx - widthY * cols) * unitWidth

            val srcRect = Rect(sx, sy, sx + unitWidth, sy + unitHeight)
            val dstRect = Rect(dx, dy, dx + unitWidth, dy + unitHeight)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        val buffer = okio.Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 100, buffer.outputStream())

        // Recycle bitmaps to free memory early
        bitmap.recycle()
        result.recycle()

        return buffer.asResponseBody("image/jpeg".toMediaTypeOrNull())
    }

    private fun Element.imgAttr() = when {
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("srcset") -> absUrl("srcset").substringBefore(" ")
        else -> absUrl("src")
    }

    private fun isRemoveTitleVersion() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_KEY_CUSTOM_UA
            title = "Custom user agent string"
            summary = "Leave blank to use the default user agent string"
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    Headers.headersOf("User-Agent", newValue as String)
                    Toast.makeText(screen.context, "Restart app to apply changes", Toast.LENGTH_SHORT).show()
                    true
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(screen.context, "Invalid user agent string: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }.also(screen::addPreference)
    }

    private val titleRegex: Regex by lazy {
        Regex(
            """^(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩)\s*)+|(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/\s*Official)\s*)+$""",
            RegexOption.IGNORE_CASE,
        )
    }

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val jsFilters =
        listOf("jQuery", "document", "getContext", "toDataURL", "getImageData", "width", "height")

    private val keyLocationRegex by lazy {
        Regex("""str\.charAt\(\s*(\d+)\s*\)""")
    }

    private val imgSrcsRegex by lazy {
        Regex("""var imgsrcs\s*=\s*['"]([a-zA-Z0-9+=/]+)['"]""")
    }

    private val colsRegex =
        Regex("""var\s*widthnum\s*=\s*heightnum\s*=\s*(\d+);""")

    private val replacePosBytecode by lazy {
        QuickJs.create().use {
            it.compile(
                """
                function replacePos(strObj, pos, replacetext) {
                    var str = strObj.substr(0, pos) + replacetext + strObj.substring(pos + 1, strObj.length);
                    return str;
                }
                """.trimIndent(),
                "?",
            )
        }
    }

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua_"
        private const val ALT_NAME_PREFIX = "Alternative Names:"
        private val ALT_NAME_SLASH_SEMICOLON_REGEX = Regex("[/;]")
        private val ALT_NAME_COMMA_REGEX = Regex(",")
    }
}
