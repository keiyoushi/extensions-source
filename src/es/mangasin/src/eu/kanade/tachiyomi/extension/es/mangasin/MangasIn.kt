package eu.kanade.tachiyomi.extension.es.mangasin

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.multisrc.mmrcms.SuggestionDto
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.synchrony.Deobfuscator
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.cryptoJsDecrypt
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.BadPaddingException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangasIn : MMRCMS() {

    override val supportsAdvancedSearch = false

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1, 1.seconds) { it.host == baseUrl.toHttpUrl().host && it.pathSegments.first() != "uploads" }

    override fun latestUpdatesUrl(page: Int) = "$baseUrl/lasted?p=$page"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<LatestUpdateResponse>()
        val manga = data.data.map {
            SManga.create().apply {
                url = "/$itemPath/${it.slug}"
                title = it.name
                thumbnail_url = guessCover(url, null)
            }
        }
        val hasNextPage = response.request.url.queryParameter("p")!!.toInt() < data.totalPages

        return MangasPage(manga, hasNextPage)
    }

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList): String {
        if (query.isEmpty()) {
            return super.searchMangaUrl(page, query, filters)
        }

        // Site's minimum: 3 bytes of the trimmed query
        if (query.trim().encodeToByteArray().size < 3) {
            throw Exception("La búsqueda debe tener al menos 3 caracteres")
        }

        return "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
        }.build().toString()
    }

    override fun searchMangaParse(response: Response, page: Int): MangasPage {
        val searchType = response.request.url.pathSegments.last()

        if (searchType != "search") {
            return super.searchMangaParse(response, page)
        }

        return parseSearchDirectory(response.parseAs<List<SuggestionDto>>(), page)
    }

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        status = when (document.selectFirst("div.manga-name span.label")?.text()?.lowercase()) {
            in detailStatusComplete -> SManga.COMPLETED
            in detailStatusOngoing -> SManga.ONGOING
            in detailStatusDropped -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private var key = ""

    private suspend fun getKey(): String {
        val script = client.get("$baseUrl/js/ads2.js", Fetch.SCRIPT).body.string()
        val deobfuscatedScript = Deobfuscator.deobfuscateScript(script)
            ?: throw Exception("No se pudo desofuscar el script")

        val variable = KEY_REGEX.find(deobfuscatedScript)?.groupValues?.get(1)
            ?: throw Exception("No se pudo encontrar la clave")

        if (variable.startsWith("'")) return variable.removeSurrounding("'")
        if (variable.startsWith("\"")) return variable.removeSurrounding("\"")

        val varRegex = """(?:let|var|const)\s+$variable\s*=\s*['|"](.*)['|"]""".toRegex()
        return varRegex.find(deobfuscatedScript)?.groupValues?.get(1)
            ?: throw Exception("No se pudo encontrar la clave")
    }

    private suspend fun decrypt(cipherText: ByteArray): ByteArray {
        val fresh = key.isEmpty()
        if (fresh) key = getKey()
        return try {
            cipherText.cryptoJsDecrypt(key)
        } catch (e: BadPaddingException) {
            if (fresh) throw e
            // Key rotated since it was read
            key = getKey()
            cipherText.cryptoJsDecrypt(key)
        }
    }

    override suspend fun chapterListParse(document: Document): List<SChapter> {
        val mangaUrl = document.location().removeSuffix("/")
        val encodeChapterData = CHAPTER_DATA_REGEX.find(document.html())?.value ?: throw Exception("No se pudo encontrar la lista de capítulos")
        val unescapedChapterData = encodeChapterData.unescape()
        val chapterData = unescapedChapterData.parseAs<CDT>()
        val salt = chapterData.s.decodeHex()

        val unsaltedCipherText = Base64.decode(chapterData.ct, Base64.DEFAULT)
        val cipherText = SALTED + salt + unsaltedCipherText

        val chapters = decrypt(cipherText).decodeToString().parseAs<String>().parseAs<List<Chapter>>()

        return chapters.map {
            SChapter.create().apply {
                name = if (it.name == "Capítulo ${it.number}") {
                    it.name
                } else {
                    "Capítulo ${it.number}: ${it.name}"
                }

                chapter_number = it.number.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParseDateTime(it.createdAt)

                setUrlWithoutDomain("$mangaUrl/${it.slug}")
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> = document.select("#all > img.img-responsive").mapIndexed { i, it ->
        var url = it.imgAttr()
        if (url.toHttpUrlOrNull() == null) {
            val decodedB64 = Base64.decode(url.substringAfter("://"), Base64.DEFAULT).decodeToString()
            url = URLDecoder.decode(decodedB64, "UTF-8")
        }
        Page(i, imageUrl = url)
    }

    private fun String.unescape(): String = UNESCAPE_REGEX.replace(this, "$1")
}

private val UNESCAPE_REGEX = """\\(.)""".toRegex()
private val CHAPTER_DATA_REGEX = """\{(?=.*\\"ct\\")(?=.*\\"iv\\")(?=.*\\"s\\").*?\}""".toRegex()
private val KEY_REGEX = """decrypt\(.*?,\s*(.*?)\s*,.*\)""".toRegex()
private val SALTED = "Salted__".encodeToByteArray()
