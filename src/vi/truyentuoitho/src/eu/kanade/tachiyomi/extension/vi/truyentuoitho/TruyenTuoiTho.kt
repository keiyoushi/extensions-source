package eu.kanade.tachiyomi.extension.vi.truyentuoitho

import android.util.Base64
import android.util.LruCache
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TruyenTuoiTho : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3).addInterceptor(::renewDriveSession)

    override val filterNonMangaItems = false

    override val chapterMode = ChapterMode.MangaAjaxPaginated

    // Plain chapters hold every page image in one .text-left block
    override val pageListParseSelector = "div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item)) img"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val defaultPages = super.parsePages(document)
        if (defaultPages.isNotEmpty()) return defaultPages

        val driveFiles = document.select(DRIVE_FRAME).map { it.absUrl("src").toHttpUrl().pathSegments[2] }
        if (driveFiles.isNotEmpty()) {
            return driveFiles.flatMap { drivePages(it, chapterUrl) }.mapIndexed { index, page -> Page(index, url = page.url, imageUrl = page.imageUrl) }
        }

        val payload = document.select("div.reading-content script").firstNotNullOfOrNull { decodeProtectedPayload(it.data()) }
            ?: return emptyList()
        return payload.parseAs<ChapterImagesPayload>().images.mapIndexed { index, imageUrl -> Page(index, url = document.location(), imageUrl = imageUrl) }
    }

    private fun decodeProtectedPayload(script: String): String? {
        if (!script.contains("split('').reverse().join('')")) return null
        val match = protectedPayloadRegex.find(script) ?: error("Protected payload not found")
        val key = atobRegex.findAll(match.groupValues[1]).joinToString("") { it.groupValues[1].decodeBase64() }
        return match.groupValues[2].reversed().decodeBase64().xorWithKey(key)
    }

    private fun String.decodeBase64(): String = String(Base64.decode(this, Base64.DEFAULT), Charsets.UTF_8)

    private fun String.xorWithKey(key: String): String {
        val output = CharArray(length)
        forEachIndexed { index, ch ->
            output[index] = (ch.code xor key[index % key.length].code).toChar()
        }
        return String(output)
    }

    // Drive's preview of a PDF serves each page as an image through a viewer session; the fragment holds the file id
    private suspend fun drivePages(fileId: String, chapterUrl: String): List<Page> {
        val previewUrl = drivePreviewUrl(fileId)
        val uploadUrl = driveUploadUrl(client.get(previewUrl, Fetch.DOCUMENT.from(chapterUrl)).body.string())
        val session = client.get(uploadUrl, Fetch.XHR.from(previewUrl)).parseAs<DriveSessionDto>(transform = ::stripXssiPrefix)
        val meta = client.get(uploadUrl.resolve(session.meta)!!, Fetch.XHR.from(previewUrl)).parseAs<DriveMetaDto>(transform = ::stripXssiPrefix)
        val imageUrl = uploadUrl.resolve(session.img)!!.newBuilder()
            .addQueryParameter("w", minOf(DRIVE_PAGE_WIDTH, meta.maxPageWidth).toString())
            .addQueryParameter("webp", "true")
            .fragment(fileId)
            .build()
        driveSessions.put(fileId, imageUrl.queryParameter("id")!!)
        return List(meta.pages) { page ->
            Page(page, url = previewUrl.toString(), imageUrl = imageUrl.newBuilder().addQueryParameter("page", page.toString()).build().toString())
        }
    }

    // Mihon keeps an opened chapter's page links: send the file's current viewer session, and renew it when Drive refuses it
    private fun renewDriveSession(chain: Interceptor.Chain): Response {
        val fileId = chain.request().url.fragment
        if (chain.request().url.host != DRIVE_HOST || fileId == null) return chain.proceed(chain.request())
        val request = driveSessions.get(fileId)?.let { chain.request().withDriveSession(it) } ?: chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful || response.code == 429) return response
        response.close()

        val sessionId = try {
            renewedSession(fileId, request.url.queryParameter("id"))
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e)
        }
        return chain.proceed(request.withDriveSession(sessionId))
    }

    private fun Request.withDriveSession(sessionId: String) = newBuilder().url(url.newBuilder().setQueryParameter("id", sessionId).build()).build()

    // Latest viewer session per Drive file
    private val driveSessions = LruCache<String, String>(20)

    @Synchronized
    private fun renewedSession(fileId: String, refusedId: String?): String {
        driveSessions.get(fileId)?.let { if (it != refusedId) return it }
        val previewUrl = drivePreviewUrl(fileId)
        val uploadUrl = driveUploadUrl(callBlocking(previewUrl, Fetch.DOCUMENT))
        val session = callBlocking(uploadUrl, Fetch.XHR.from(previewUrl)).parseAs<DriveSessionDto>(transform = ::stripXssiPrefix)
        return uploadUrl.resolve(session.img)!!.queryParameter("id")!!.also { driveSessions.put(fileId, it) }
    }

    private fun callBlocking(url: HttpUrl, fetch: Fetch): String = client.newCall(GET(url, headers).newBuilder().fetch(fetch).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP error ${response.code}")
        response.body.string()
    }

    private fun drivePreviewUrl(fileId: String) = HttpUrl.Builder()
        .scheme("https")
        .host(DRIVE_HOST)
        .addPathSegments("file/d")
        .addPathSegment(fileId)
        .addPathSegment("preview")
        .build()

    // The preview page keeps the session link as a string in its viewer data
    private fun driveUploadUrl(preview: String): HttpUrl {
        val link = driveUploadRegex.find(preview)?.value ?: throw IOException("Google Drive can't preview this file")
        return link.parseAs<String>().toHttpUrl()
    }

    // Drive's viewer JSON starts with a )]}' line
    private fun stripXssiPrefix(body: String) = body.substringAfter('\n')

    companion object {
        private const val DRIVE_HOST = "drive.google.com"
        private const val DRIVE_FRAME = "iframe[src*=drive.google.com/file/d/]"

        // Above the scans' own width; the viewer's maximum (3200) only upscales them
        private const val DRIVE_PAGE_WIDTH = 1600
        private val driveUploadRegex = Regex(""""https://drive\.google\.com/viewer/upload\?[^"]+"""")

        // Key: every atob() piece in the const, "a=atob(..),b=atob(..)" or "d=atob(..)+atob(..)"; payload: the string after them
        private val protectedPayloadRegex = Regex("""const\s+((?:\w+=)?(?:atob\('[^']+'\)[+,]\s*(?:\w+=)?)+)'([^']+)'""")
        private val atobRegex = Regex("""atob\('([^']+)'\)""")
    }
}
