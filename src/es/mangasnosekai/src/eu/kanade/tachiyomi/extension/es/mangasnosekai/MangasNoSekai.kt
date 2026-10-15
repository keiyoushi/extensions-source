package eu.kanade.tachiyomi.extension.es.mangasnosekai

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.lib.synchrony.Deobfuscator
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangasNoSekai : MadaraNoAjax() {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("es"))

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) {
        it.host == baseUrl.toHttpUrl().host
    }

    override fun archiveSelector() = "div.page-listing-item > div.row > div"
    override val archiveUrlSelector = "a[href]"
    override val archiveTitleSelector = "figcaption"

    override val mangaDetailsSelectorTitle = "div.thumble-container p.titleMangaSingle"
    override val mangaDetailsSelectorThumbnail = "div.thumble-container img.img-responsive"
    override val mangaDetailsSelectorDescription = "section#section-sinopsis > p"
    override val mangaDetailsSelectorStatus = "section#section-sinopsis div.d-flex:has(div:contains(Estado)) p"
    override val mangaDetailsSelectorAuthor = "section#section-sinopsis div.d-flex:has(div:contains(Autor)) p a"
    override val mangaDetailsSelectorGenre = "section#section-sinopsis div.d-flex:has(div:contains(Generos)) p a"
    override val altNameSelector = "section#section-sinopsis div.d-flex:has(div:contains(Otros nombres)) p"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    private suspend fun getChapters(
        url: String,
        mangaId: String,
        page: Int,
        objects: List<Pair<String, String>>,
    ): ChapterWrapper {
        val form = FormBody.Builder()
            .add("mangaid", mangaId)
            .add("page", page.toString())

        objects.forEach { (key, value) ->
            form.add(key, value)
        }

        return client.post(baseUrl + url, xhrHeaders, form.build()).parseAs<ChapterWrapper>()
    }

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ): List<SChapter> {
        val coreScript = mangaPage!!.selectFirst("script#wp-manga-js")!!.attr("abs:src")
        val coreScriptBody = Deobfuscator.deobfuscateScript(client.get(coreScript, headers).body.string())
            ?: throw Exception("No se pudo deobfuscar el script")

        val regexCapture = ACTION_REGEX.find(coreScriptBody)?.groupValues
        val url = regexCapture?.get(1) ?: throw Exception("No se pudo obtener la url del capítulo")
        val data = regexCapture.getOrNull(2)?.trim() ?: throw Exception("No se pudo obtener la data del capítulo")

        val objects = OBJECTS_REGEX.findAll(data)
            .mapNotNull { matchResult ->
                val key = matchResult.groupValues[1]
                val value = matchResult.groupValues.getOrNull(2)
                if (!value.isNullOrEmpty()) key to value else null
            }.toList()

        val chapterList = mutableListOf<SChapter>()
        val mangaId = mangaPage.mangaId()!!

        val first = getChapters(url, mangaId, 1, objects)
        chapterList.addAll(first.chapters.map { it.toSChapter() })

        coroutineScope {
            (2..first.totalPages).map { page ->
                async {
                    getChapters(url, mangaId, page, objects)
                }
            }.awaitAll().forEach { result ->
                chapterList.addAll(result.chapters.map { it.toSChapter() })
            }
        }

        return chapterList
    }

    private fun Chapter.toSChapter() = SChapter.create().apply {
        name = this@toSChapter.name
        val cleanDate = Jsoup.parseBodyFragment(this@toSChapter.date).wholeText()
        date_upload = parseChapterDate(cleanDate)
        setUrlWithoutDomain(this@toSChapter.url.removeSuffix("/"))
    }

    override val supportsFilterFetching = false

    override suspend fun fetchChapterDocument(chapterUrl: String) = super.fetchChapterDocument(chapterUrl.trimEnd('/') + "/")

    companion object {
        val ACTION_REGEX = """function\s+.*?[\s\S]*?\.ajax;?[\s\S]*?(?:'?url'?:\s*'([^']*)')(?:[\s\S]*?'?data'?:\s*\{([^}]*)\})?""".toRegex()
        val OBJECTS_REGEX = """\s*'?(\w+)'?\s*:\s*(?:(?:'([^']*)'|([^,\r\n]+))\s*,?\s*)""".toRegex()
        val MANGA_ID_REGEX = """\"manga_id"\s*:\s*"(.*)\"""".toRegex()
        val ALT_MANGA_ID_REGEX = """\"postId"\s*:\s*"(.*)\"""".toRegex()
    }
}
