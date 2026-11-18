package eu.kanade.tachiyomi.extension.es.aedex

import eu.kanade.tachiyomi.multisrc.catharsis.Catharsis
import eu.kanade.tachiyomi.multisrc.catharsis.ChapterDto
import eu.kanade.tachiyomi.multisrc.catharsis.MangaDto
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@Source
abstract class Aedex : Catharsis() {

    override val apiUrl get() = "$baseUrl/api/backend"

    override val apiHeaders get() = headersBuilder()
        .set("System", "aedex")
        .set("X-FK-Sistema", "1")
        .build()

    override val mangaPath = "comic"

    override fun mangaKey(manga: MangaDto) = manga.slug

    override fun OkHttpClient.Builder.addReaderInterceptors(): OkHttpClient.Builder = addInterceptor(ImageInterceptor())

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$mangaPath/${chapter.url}"

    // fecha_publicacion has no offset; the site shows date_created
    override fun chapterDate(chapter: ChapterDto) = Instant.tryParse(chapter.dateCreated)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val fetch = Fetch.XHR.from(getChapterUrl(chapter))
        val freeReader = FreeReader()
        val readerHeaders = apiHeaders.newBuilder()
            .set("X-Catharsis-Reader-Pass", readerPass(fetch).pass)
            .set("X-Catharsis-Ws-Pub", freeReader.publicKey)
            .build()
        val chapterUrl = "$apiUrl/mangas/${chapter.url}".toHttpUrl().newBuilder()
            .addQueryParameter("modo", "gratis")
            .build()
        val free = client.get(chapterUrl, readerHeaders, fetch, ensureSuccess = false).ensureSiteSuccess().parseAs<FreeReaderDto>()
        // WebSockets stay on OkHttp: Cronet can't upgrade a connection
        val ready = freeReader.countdown(network.client, headersBuilder().set("Origin", baseUrl).build(), free.wsUrl, free.wsTicket)
        val reader = client.post(
            ready.redirectUrl!!,
            Fetch.DOCUMENT.from(getChapterUrl(chapter)),
            FormBody.Builder().add("data", ready.redirectData!!).build(),
        ).asJsoup()

        val canvases = reader.select("canvas.manga-page-canvas")
        if (canvases.isEmpty()) {
            throw IOException("No se encontraron páginas en el capítulo")
        }

        val readerUrl = reader.location()
        val html = reader.html()

        val seed = reader.getElementById("bv-vault")?.attr("data-bv-seed")
            ?.takeIf(String::isNotBlank)
            ?: SEED_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("No se encontró la clave de seguridad del lector (seed)")

        val lectorJson = reader.select("script").firstNotNullOfOrNull { script ->
            LECTOR_REGEX.find(script.data())?.groupValues?.get(1)
                ?: LECTOR_REGEX.find(script.html())?.groupValues?.get(1)
        } ?: LECTOR_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("No se encontró la configuración del lector")

        val lector = lectorJson.parseAs<LectorDto>()
        val clockOffset = lector.now - Clock.System.now().toEpochMilliseconds()
        val readerOrigin = readerUrl.toHttpUrl().let { "${it.scheme}://${it.host}" }
        val fragment = "${lector.t}:$seed:${lector.h}:$clockOffset:${lector.xorByte}"

        return canvases
            .filter { it.hasAttr("data-h") && it.attr("data-h").isNotBlank() }
            .mapIndexed { index, canvas ->
                val handle = canvas.attr("data-h")
                val imageUrl = readerOrigin.toHttpUrl().newBuilder()
                    .addPathSegments("lector/p/$handle")
                    .addQueryParameter("q", "hq")
                    .fragment(fragment)
                    .build()
                    .toString()
                Page(index, url = readerUrl, imageUrl = imageUrl)
            }
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(Fetch.XHR.from(page.url))
        .build()

    companion object {
        private val LECTOR_REGEX = Regex("""window\.__LECTOR__\s*=\s*(\{.+?\});?""")
        private val SEED_REGEX = Regex("""data-bv-seed[\\":= ]+([a-f0-9]{32})""")
    }
}
