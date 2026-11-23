package eu.kanade.tachiyomi.extension.es.aedex

import android.util.Base64
import android.util.LruCache
import eu.kanade.tachiyomi.multisrc.catharsis.Catharsis
import eu.kanade.tachiyomi.multisrc.catharsis.ChapterDto
import eu.kanade.tachiyomi.multisrc.catharsis.MangaDto
import eu.kanade.tachiyomi.multisrc.catharsis.ReaderPassDto
import eu.kanade.tachiyomi.multisrc.catharsis.ReaderPassRequest
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
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

    override fun OkHttpClient.Builder.addReaderInterceptors(): OkHttpClient.Builder = addInterceptor(ImageInterceptor(::renewSession))

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$mangaPath/${chapter.url}"

    // fecha_publicacion has no offset; the site shows date_created
    override fun chapterDate(chapter: ChapterDto) = Instant.tryParse(chapter.dateCreated)

    // Signs the site's reader calls; its keys come with the chapter page
    @Volatile
    private var signer: ReaderSigner? = null

    // The reader pass is the site's `ax_rp` cookie, bound to the connection's IP
    override fun storedReaderPass(): ReaderPassDto? = client.cookieJar.loadForRequest("$baseUrl/lector/".toHttpUrl())
        .firstOrNull { it.name == PASS_COOKIE }
        ?.let { ReaderPassDto(it.value, it.expiresAt) }

    override suspend fun redeemReaderPass(capToken: String, sitekey: String, fetch: Fetch): ReaderPassDto {
        val signer = signer ?: throw IOException("Reader keys not found")
        val headers = headersBuilder().set(signer.buildName, signer.buildId).build()
        client.post(
            "$baseUrl/lector/pase",
            headers,
            fetch,
            ReaderPassRequest(capToken, sitekey).toJsonRequestBody(),
            ensureSuccess = false,
        ).use { response ->
            if (response.code == 409) throw OutdatedReaderException()
            if (!response.isSuccessful) throw response.siteError(response.apiError()?.code)
        }
        return storedReaderPass() ?: throw IOException("The site didn't hand out a reader pass")
    }

    // 409: the site rotated its reader keys, the chapter page has the new ones
    private class OutdatedReaderException : IOException("The site's reader changed, try again")

    private suspend fun requestFreeReader(chapter: SChapter, fetch: Fetch, freeReader: FreeReader): FreeReaderDto {
        val slug = chapter.url.substringBeforeLast('/')
        // The site sends the number as parseFloat does
        val number = chapter.url.substringAfterLast('/').let { it.toDoubleOrNull()?.toString()?.removeSuffix(".0") ?: it }
        val body = FreeReaderRequest(slug, number, "gratis").toJsonRequestBody()
        var renewPass = false
        for (attempt in 1..MAX_ATTEMPTS) {
            val retry = attempt < MAX_ATTEMPTS
            try {
                val readerSigner = signer ?: loadSigner(chapter).also { signer = it }
                readerPass(fetch, renewPass)
                val time = Clock.System.now().toEpochMilliseconds()
                val (proofName, proof) = readerSigner.proof("c", "$slug/$number", time)
                val headers = headersBuilder()
                    .set("X-Catharsis-Ws-Pub", freeReader.publicKey)
                    .set(readerSigner.buildName, readerSigner.buildId)
                    .set(proofName, proof)
                    .build()
                client.post("$baseUrl/lector/capitulo", headers, fetch, body, ensureSuccess = false).use { response ->
                    when {
                        response.isSuccessful -> return response.parseAs<FreeReaderDto>()
                        response.code == 409 && retry -> signer = null
                        else -> {
                            val code = response.apiError()?.code
                            when {
                                // The pass is gone, or was given to another IP: solve a new one
                                response.code == 403 && code == "READER_PASS_REQUIRED" && retry -> renewPass = true
                                code == "LOGIN_REQUIRED" -> throw IOException(LOGIN_REQUIRED_MESSAGE)
                                else -> throw response.siteError(code)
                            }
                        }
                    }
                }
            } catch (_: OutdatedReaderException) {
                if (!retry) throw OutdatedReaderException()
                signer = null
            }
        }
        throw IOException("Couldn't open the reader")
    }

    private suspend fun loadSigner(chapter: SChapter): ReaderSigner {
        val doc = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        doc.selectFirst("[data-t][data-s]")?.let { vault ->
            return ReaderSigner("ax", vault.attr("data-t"), vault.attr("data-s"))
        }
        val hexAttrs = doc.select("[hidden]").firstNotNullOfOrNull { el ->
            el.attributes()
                .filter { it.key.startsWith("data-") && HEX_32_REGEX.matches(it.value) }
                .takeIf { it.size == 2 }
        } ?: throw IOException("Reader keys not found")
        return ReaderSigner("ax", hexAttrs[0].value, hexAttrs[1].value)
    }

    private suspend fun openReader(chapter: SChapter): Document {
        val fetch = Fetch.XHR.from("${getChapterUrl(chapter)}?modo=gratis")
        val freeReader = FreeReader()
        val free = requestFreeReader(chapter, fetch, freeReader)
        // WebSockets stay on OkHttp: Cronet can't upgrade a connection
        val wsHeaders = headersBuilder().set("Origin", baseUrl).build()
        val ready = freeReader.countdown(network.client, wsHeaders, free.wsUrl, free.wsTicket)
        return client.post(
            ready.redirectUrl!!,
            Fetch.DOCUMENT.from(getChapterUrl(chapter)),
            FormBody.Builder().add("data", ready.redirectData!!).build(),
        ).asJsoup()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val session = readerSession(openReader(chapter))
        sessions.put(chapter.url, session)
        return List(session.pageCount) { index ->
            Page(index, url = session.readerUrl, imageUrl = session.imageUrl(index, chapter.url))
        }
    }

    private fun readerSession(reader: Document): ReaderSession {
        // Same lookups as the site's own reader script
        val seed = reader.selectFirst("meta[name=reader-token]")?.attr("content")
            ?: reader.getElementById("bv-frame")?.attr("data-seed")
            ?: throw IOException("Reader seed not found")
        val lector = reader.select("script").firstNotNullOfOrNull { LECTOR_REGEX.find(it.data())?.groupValues?.get(1) }
            ?.let { Base64.decode(it, Base64.DEFAULT).decodeToString().parseAs<LectorDto>() }
            ?: throw IOException("Reader keys not found")
        val handles = lector.pages.ifEmpty {
            reader.select("canvas.manga-page-canvas").mapNotNull { it.attrOrNull("data-cid") ?: it.attrOrNull("data-h") }
        }.ifEmpty { throw IOException("Reader pages not found") }
        return ReaderSession(
            readerUrl = reader.location(),
            keys = VisitKeys(lector.token, seed, lector.liveKey, lector.now - Clock.System.now().toEpochMilliseconds()),
            handles = handles,
        )
    }

    private val sessions = LruCache<String, ReaderSession>(10)
    private val sessionLock = Mutex()

    // The page list Mihon caches can hold the keys of a visit the site forgot:
    // a new visit replaces it, unless another page request already made one
    private fun renewSession(chapterKey: String, staleSeed: String): ReaderSession? = runBlocking {
        sessionLock.withLock {
            sessions[chapterKey]?.takeIf { it.keys.seed != staleSeed }
                ?: readerSession(openReader(SChapter.create().apply { url = chapterKey })).also { sessions.put(chapterKey, it) }
        }
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(Fetch.XHR.from(page.url))
        .build()

    companion object {
        private const val PASS_COOKIE = "ax_rp"
        private const val MAX_ATTEMPTS = 3
        private val HEX_32_REGEX = Regex("^[0-9a-f]{32}$")
        private val LECTOR_REGEX = Regex("""window\.(?:readerContext|__BV_CTX__)\s*=\s*JSON\.parse\(atob\("([^"]+)"\)\)""")
    }
}

internal const val LOGIN_REQUIRED_MESSAGE = "Inicia sesión en WebView para leer este capítulo"
