package eu.kanade.tachiyomi.extension.id.mangaku

import android.net.Uri
import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.security.MessageDigest

class Mangaku : ParsedHttpSource() {

    override val name = "Mangaku"

    override val baseUrl = "https://mangaku.blog"

    override val lang = "id"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private lateinit var directory: Elements

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "$baseUrl/")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int): Request =
        POST(
            "$baseUrl/daftar-komik-bahasa-indonesia/",
            headers,
            FormBody.Builder().add("ritem", "hot").build(),
        )

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        directory = document.select(popularMangaSelector())
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val manga = mutableListOf<SManga>()
        val end = ((page * 24) - 1).let { if (it <= directory.lastIndex) it else directory.lastIndex }

        for (i in (((page - 1) * 24)..end)) {
            manga.add(popularMangaFromElement(directory[i]))
        }
        return MangasPage(manga, end < directory.lastIndex)
    }

    override fun popularMangaSelector() = "#data .npx .an a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.ownText()
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.kiri_anime div.utao, div.proyek div.utao"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.uta div.luf a.series").attr("href"))
        title = element.select("div.uta div.luf a.series").attr("title")
        thumbnail_url = element.select("div.uta div.imgu img").attr("abs:data-src")
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search/$query/", headers)

    override fun searchMangaSelector() = ".listupd .bs"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select(".bsx a").attr("href"))
        title = element.select(".bigor .tt a").text()
        thumbnail_url = element.select(".bsx img").attr("abs:data-src")
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select(".post.singlep .titles a").text().replace("Bahasa Indonesia", "").trim()
        thumbnail_url = document.select(".post.singlep img").attr("abs:src")
        document.select("#wrapper-a #content-a .inf").forEach {
            when (it.select(".infx").text()) {
                "Genre" -> genre = it.select("p a[rel=tag]").joinToString { it.text() }
                "Author" -> author = it.select("p").text()
                "Sinopsis" -> description = it.select("p").text()
            }
        }
    }

    override fun chapterListSelector() = "#content-b > div > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text().let {
            if (it.contains("–")) {
                it.split("–")[1].trim()
            } else {
                it
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val wpRoutineUrl = document.selectFirst("script[src*=wp-routine]")!!.attr("abs:src")
        Log.d("mangaku", "wp-routine: $wpRoutineUrl")

        val wpRoutineJs = client.newCall(GET(wpRoutineUrl, headers)).execute().use {
            it.body.string()
        }
        val upt3 = wpRoutineJs
            .substringAfterLast("upt3(")
            .substringBefore(");")
        val appMgk = wpRoutineJs
            .substringAfter("const $upt3 = '")
            .substringBefore("'")
            .reversed()
        Log.d("mangaku", "app-mgk: $appMgk")

        val dtxScript = document.selectFirst("script:containsData(var dtx =)")!!.html()
        val dtxIsEqualTo = dtxScript
            .substringAfter("var dtx = ")
            .substringBefore(";")
        val dtx = dtxScript
            .substringAfter("var $dtxIsEqualTo= \"")
            .substringBefore("\"")

        val mainScriptTag = document.selectFirst("script:containsData(await jrsx)")!!.html()
        val jrsxArgs = mainScriptTag
            .substringAfter("await jrsx(")
            .substringBefore(");")
            .split(",")
        Log.d("mangaku", "args: $jrsxArgs")

        val thirdArgValue = mainScriptTag
            .substringAfter("const ${jrsxArgs[2]} = '")
            .substringBefore("'")
        Log.d("mangaku", "arg2: $thirdArgValue")

        val encodedAttr = jrsxArgs[4].removeSurrounding("'")

        val upt4arg = mainScriptTag
            .substringAfter("const ${jrsxArgs[3]} = await upt4('")
            .substringBefore("'")
        Log.d("mangaku", "upt4arg: $upt4arg")
        val upt4value = upt4(appMgk, upt4arg)

        val decrypted = CryptoAES.decrypt(dtx, huzorttshj(thirdArgValue, upt4value))
            .replace(rsxxxRe, "")
            .replace("_", "=")
            .reversed()
            .replace("+", "%20")

        val htmImageList = Base64.decode(decrypted, Base64.DEFAULT)
            .toString(Charsets.UTF_8)
            .percentDecode()

        val attr = stringRotator(encodedAttr, 23, 69, 9).lowercase()
        val fifthArgValueDigest =
            stringRotator(digest("SHA384", encodedAttr), 23, 69, 20).lowercase()

        val re = Regex("""$attr=['"](.*?)['"]""")
        return re.findAll(htmImageList).mapIndexed { idx, it ->
            val url = Base64.decode(
                CryptoAES.decrypt(it.groupValues[1], fifthArgValueDigest),
                Base64.DEFAULT,
            )
                .toString(Charsets.UTF_8)
                .replace("+", "%20")
                .percentDecode()
            Page(idx, imageUrl = url)
        }.toList()
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    private val rsxxxRe = Regex(""".............?\+.......""")

    private val noLetterRe = Regex("""[^a-z]""")

    private val noNumberRe = Regex("""[^0-9]""")

    private val whitespaceRe = Regex("""\s+""")

    private fun huzorttshj(key: String, upt4val: String): String {
        val mapping = "-ABCDEFGHIJKLMNOPQRSTUVWXYZ=0123456789abcdefghijklmnopqrstuvwxyz+"
        val b64upt4 = btoa(upt4val).replace(whitespaceRe, "")
        var idx = 0
        return b64upt4.map {
            val upt4idx = mapping.indexOf(it)
            val keyidx = mapping.indexOf(key[idx])

            val output = (mapping.substring(keyidx) + mapping.substring(0, keyidx))[upt4idx]

            if (idx == key.length - 1) {
                idx = 0
            } else {
                idx += 1
            }
            output
        }.joinToString("")
    }

    private fun upt4(appMgk: String, key: String): String {
        val fullKey = key + appMgk.map {
            (it.code xor 71).toChar()
        }.joinToString("")

        val b64FullKey = btoa(fullKey)

        val sixLastChars = b64FullKey.substring(b64FullKey.length - 7, b64FullKey.length - 1)
        val elevenFirstChars = b64FullKey.substring(0, 12)
        val keyFragment = btoa(sixLastChars + elevenFirstChars).trim()
        val firstDigest = digest("SHA384", keyFragment)

        val uniqueLetters = firstDigest.replace(noLetterRe, "").distinct()
        val uniqueNumbers = firstDigest.replace(noNumberRe, "").distinct()
        val joined = uniqueNumbers + uniqueLetters

        val secondDigest = digest("SHA1", joined)
        val secondDigestReversed = secondDigest.reversed()

        val rotated = stringRotator(secondDigestReversed) + "-$key"
        return keyFragment + joined + secondDigestReversed + rotated
    }

    private fun stringRotator(
        input: String,
        multiplier: Int = 73,
        adder: Int = 93,
        length: Int = 20,
        strings: List<String> = listOf("PEWAW", "MJKJG", "SGWRT", "KUIQ"),
    ): String {
        val firstPass = input
            .map { input.length * multiplier + (adder + it.code) }
            .joinToString("") + adder.toString()
        return firstPass.map {
            val idx = it.toString().toInt()
            if (idx < strings.size) strings[idx] else idx.toString()
        }
            .joinToString("")
            .padEnd(length)
            .substring(0, length)
    }

    private fun btoa(input: String): String =
        Base64.encode(input.toByteArray(), Base64.DEFAULT).toString(Charsets.UTF_8)

    private fun digest(digest: String, input: String): String =
        MessageDigest.getInstance(digest).digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun String.distinct(): String = toCharArray().distinct().joinToString("")

    private fun String.percentDecode(): String = Uri.decode(this)
}
