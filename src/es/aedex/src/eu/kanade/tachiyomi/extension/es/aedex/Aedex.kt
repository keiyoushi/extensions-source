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
import java.net.URLEncoder
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

    override fun OkHttpClient.Builder.addReaderInterceptors(): OkHttpClient.Builder = addInterceptor(ImageInterceptor(network.client))

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

        val isNewReader = canvases.any { it.hasAttr("data-h") }
        if (!isNewReader) {
            val xorKey = reader.select("script:not([src])").firstNotNullOfOrNull { XOR_KEY_REGEX.find(it.data()) }?.groupValues?.get(1).orEmpty()
            return canvases
                .filter { it.hasAttr("data-src") && it.attr("data-src").isNotBlank() }
                .mapIndexed { index, canvas ->
                    val src = canvas.absUrl("data-src").takeIf(String::isNotBlank)
                        ?: baseUrl.toHttpUrl().resolve(canvas.attr("data-src"))?.toString()
                        ?: canvas.attr("data-src")
                    val imageUrl = src.toHttpUrl().newBuilder()
                        .fragment("$xorKey:${canvas.attr("data-k")}")
                        .build()
                        .toString()
                    Page(index, url = readerUrl, imageUrl = imageUrl)
                }
        }

        val seed = SEED_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("No se encontró la clave de seguridad del lector (seed)")
        val t = T_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("No se encontró el token de seguridad del lector (t)")
        val h = H_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("No se encontró el parámetro h del lector")
        val now = NOW_REGEX.find(html)?.groupValues?.get(1)?.toLongOrNull() ?: System.currentTimeMillis()
        val xorByte = XOR_BYTE_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0x43
        val clockOffset = now - System.currentTimeMillis()

        val readerOrigin = readerUrl.toHttpUrl().let { "${it.scheme}://${it.host}" }
        val encodedRef = URLEncoder.encode(readerUrl, "UTF-8")

        return canvases
            .filter { it.hasAttr("data-h") && it.attr("data-h").isNotBlank() }
            .mapIndexed { index, canvas ->
                val handle = canvas.attr("data-h")
                val imageUrl = "$readerOrigin/lector/p/$handle/?q=hq#proto&t=$t&seed=$seed&h=$h&offset=$clockOffset&xor=$xorByte&ref=$encodedRef"
                Page(index, url = readerUrl, imageUrl = imageUrl)
            }
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(Fetch.XHR.from(page.url))
        .build()

    companion object {
        private val XOR_KEY_REGEX = Regex("""\bvar XOR_KEY\s*=\s*(\d+)""")
        private val SEED_REGEX = Regex("""data-bv-seed[\\":= ]+([a-f0-9]{32})""")
        private val T_REGEX = Regex("""["\']t["\']\s*:\s*["\']([a-f0-9]{32})["\']""")
        private val H_REGEX = Regex("""["\']h["\']\s*:\s*["\']([^"\']+)["\']""")
        private val NOW_REGEX = Regex("""["\']now["\']\s*:\s*(\d+)""")
        private val XOR_BYTE_REGEX = Regex("""["\']xorByte["\']\s*:\s*(\d+)""")
    }
}
