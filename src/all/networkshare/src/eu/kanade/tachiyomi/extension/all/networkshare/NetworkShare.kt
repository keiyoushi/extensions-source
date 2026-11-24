package eu.kanade.tachiyomi.extension.all.networkshare

import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import com.hierynomus.smbj.auth.AuthenticationContext
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.long
import keiyoushi.zip.Entry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import okio.buffer
import java.io.EOFException
import java.io.IOException
import java.util.zip.ZipException

@Source
abstract class NetworkShare :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {
    private val preferences by getPreferencesLazy()

    private val address get() = preferences.getString(PREF_ADDRESS, "")!!.trim()

    override val baseUrl get() = if (address.isEmpty()) "" else share.url("")

    private var current: ConfiguredShare? = null

    /** The share for the current settings; rebuilt when they change. */
    private val share: Share
        @Synchronized get() {
            val settings = Settings(address, preferences.getString(PREF_USERNAME, "")!!, preferences.getString(PREF_PASSWORD, "")!!)
            current?.takeIf { it.settings == settings }?.let { return it.share }
            if (settings.address.isEmpty()) throw Exception("Set the share's address in the extension settings")
            return createShare(settings).also { current = ConfiguredShare(settings, it) }
        }

    private val host get() = "s$id$HOST_SUFFIX"

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::intercept)

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(seriesFolders().sortedByDescending { it.lastModified }.map { it.toSManga() }, false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val folders = seriesFolders()
            .filter { it.name.contains(query, ignoreCase = true) }
            .let { folders ->
                if (sort?.byDate == true) folders.sortedBy { it.lastModified } else folders.sortedWith(compareBy(NATURAL_ORDER) { it.name })
            }
            .let { if (sort?.ascending == false) it.reversed() else it }
        return MangasPage(folders.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val root = share.url("").toHttpUrlOrNull() ?: return null
        if (url.host != root.host) return null
        val rootSegments = root.pathSegments.filter { it.isNotEmpty() }
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size <= rootSegments.size || segments.take(rootSegments.size) != rootSegments) return null
        val manga = SManga.create().apply {
            this.url = segments[rootSegments.size]
            title = this.url
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val files = share.list(manga.url)
        val chapterFiles = files.filter { it.isChapter }
        return SMangaUpdate(
            manga = { if (fetchDetails) mangaDetails(manga, files, chapterFiles) else manga },
            chapters = { if (fetchChapters) chapterList(manga, chapterFiles, chapters) else chapters },
        )
    }

    private suspend fun mangaDetails(manga: SManga, files: List<RemoteFile>, chapterFiles: List<RemoteFile>): SManga {
        manga.thumbnail_url = coverUrl(manga.url)
        if (files.any { !it.isDirectory && it.name == ComicInfo.FILE_NAME }) {
            ComicInfo.parse(share.read("${manga.url}/${ComicInfo.FILE_NAME}")).applyTo(manga)
            return manga
        }
        // Like the app's Local source, take the series' details from its first archive
        val first = chapterFiles.filter { !it.isEmptyFile && (it.format == Format.ZIP || it.format == Format.EPUB) }
            .minWithOrNull(compareBy(NATURAL_ORDER) { it.name })
            ?: return manga
        val path = "${manga.url}/${first.name}"
        if (first.format == Format.EPUB) {
            damagedAsNull { Epub.open(share, path) }?.applyTo(manga)
        } else {
            chapterComicInfo(path, first)?.applyTo(manga)
        }
        return manga
    }

    private suspend fun chapterList(manga: SManga, chapterFiles: List<RemoteFile>, known: List<SChapter>): List<SChapter> {
        val knownByUrl = known.associateBy { it.url }
        val permits = Semaphore(METADATA_READS)
        return coroutineScope {
            chapterFiles.sortedWith(compareByDescending(NATURAL_ORDER) { it.name }).map { file ->
                async {
                    val path = "${manga.url}/${file.name}"
                    val previous = knownByUrl[path]
                    SChapter.create().apply {
                        url = path
                        name = if (file.isDirectory) file.name else file.name.substringBeforeLast('.')
                        date_upload = file.lastModified
                        memo = buildJsonObject {
                            put(MEMO_SIZE, file.size)
                            put(MEMO_MODIFIED, file.lastModified)
                        }
                        // Metadata is read once per file version: an unchanged file keeps what it gave before
                        if (previous != null && previous.memo[MEMO_SIZE]?.long == file.size && previous.memo[MEMO_MODIFIED]?.long == file.lastModified) {
                            name = previous.name
                            chapter_number = previous.chapter_number
                            scanlator = previous.scanlator
                            date_upload = previous.date_upload
                        } else {
                            permits.withPermit { readMetadata(path, file, this) }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun readMetadata(path: String, file: RemoteFile, chapter: SChapter) {
        if (file.isEmptyFile) return
        when (file.format) {
            Format.EPUB -> damagedAsNull { Epub.open(share, path) }?.applyTo(chapter)
            Format.ZIP, Format.FOLDER -> chapterComicInfo(path, file)?.applyTo(chapter)
            Format.UNSUPPORTED, null -> Unit
        }
    }

    private suspend fun chapterComicInfo(path: String, file: RemoteFile): ComicInfo? = if (file.isDirectory) {
        share.list(path).firstOrNull { !it.isDirectory && it.name == ComicInfo.FILE_NAME }
            ?.let { ComicInfo.parse(share.read("$path/${it.name}")) }
    } else {
        damagedAsNull {
            share.zipDirectory(path).entries.firstOrNull { it.name == ComicInfo.FILE_NAME }
                ?.let { ComicInfo.parse(share.readZipEntry(path, it).buffer()) }
        }
    }

    /** A damaged archive still lists as a chapter, as in the app's Local source; only its metadata is missing. */
    private inline fun <T> damagedAsNull(read: () -> T?): T? = try {
        read()
    } catch (_: ZipException) {
        null
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = chapter.url
        return when (path.format ?: Format.FOLDER) {
            Format.ZIP -> share.zipDirectory(path).entries
                .filter { it.name.isImage() && !it.name.isJunk() }
                .sortedWith(compareBy(NATURAL_ORDER) { it.name })
                .mapIndexed { index, entry -> Page(index, imageUrl = entryUrl(path, entry)) }

            Format.EPUB -> Epub.open(share, path)?.images().orEmpty()
                .mapIndexed { index, entry -> Page(index, imageUrl = entryUrl(path, entry)) }

            Format.UNSUPPORTED -> throw Exception("Only CBZ/ZIP, EPUB and folders of images can be read; convert ${path.substringAfterLast('.')} archives to CBZ")

            Format.FOLDER -> share.list(path)
                .filter { !it.isDirectory && it.name.isImage() && !it.name.isHidden() }
                .sortedWith(compareBy(NATURAL_ORDER) { it.name })
                .mapIndexed { index, file -> Page(index, imageUrl = fileUrl("$path/${file.name}")) }
        }
    }

    override fun getMangaUrl(manga: SManga): String = share.url(manga.url)

    override fun getChapterUrl(chapter: SChapter): String = share.url(chapter.url)

    override fun getFilterList(data: JsonElement?) = FilterList(SortFilter())

    private suspend fun seriesFolders(): List<RemoteFile> = share.list("").filter { it.isDirectory && !it.name.isHidden() }

    private fun RemoteFile.toSManga() = SManga.create().apply {
        url = name
        title = name
        thumbnail_url = coverUrl(name)
    }

    private fun localUrl(kind: String) = HttpUrl.Builder().scheme("https").host(host).addPathSegment(kind)

    private fun coverUrl(series: String) = localUrl(KIND_COVER).addQueryParameter("path", series).build().toString()

    private fun fileUrl(path: String) = localUrl(KIND_FILE).addQueryParameter("path", path).build().toString()

    private fun entryUrl(path: String, entry: Entry) = localUrl(KIND_ENTRY)
        .addQueryParameter("path", path)
        .addQueryParameter("name", entry.name)
        .addQueryParameter("method", entry.method.toString())
        .addQueryParameter("size", entry.compressedSize.toString())
        .addQueryParameter("offset", entry.localHeaderOffset.toString())
        .addQueryParameter("allowance", entry.localHeaderAllowance.toString())
        .build().toString()

    /** Serves the page and cover urls on [host] from the share. */
    private fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != host) return chain.proceed(request)

        val image = try {
            runBlocking { open(url) }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        if (image == null) return response.code(404).message("Not Found").body("".toResponseBody()).build()
        val (name, source) = image
        return response.code(200).message("OK").body(source.asResponseBody(name.mimeType())).build()
    }

    private suspend fun open(url: HttpUrl): Pair<String, BufferedSource>? {
        val path = url.queryParameter("path")!!
        return when (url.pathSegments.first()) {
            KIND_FILE -> path to share.read(path)
            KIND_ENTRY -> {
                val name = url.queryParameter("name")!!
                val entry = Entry(
                    name = name,
                    method = url.queryParameter("method")!!.toInt(),
                    compressedSize = url.queryParameter("size")!!.toLong(),
                    localHeaderOffset = url.queryParameter("offset")!!.toLong(),
                    localHeaderAllowance = url.queryParameter("allowance")!!.toInt(),
                )
                val content = try {
                    share.readZipEntry(path, entry, verify = true)
                } catch (e: IOException) {
                    if (e !is ZipException && e !is EOFException) throw e
                    // The app keeps page lists: the archive changed since, so look the page up again
                    val current = share.zipDirectory(path).entries.firstOrNull { it.name == name } ?: return null
                    share.readZipEntry(path, current, verify = true)
                }
                name to content.buffer()
            }
            KIND_COVER -> cover(path)
            else -> null
        }
    }

    /** The series' cover file, else the first page of its first chapter, as the app's Local source picks it. */
    private suspend fun cover(series: String): Pair<String, BufferedSource>? {
        val files = share.list(series)
        files.firstOrNull { !it.isDirectory && it.name.substringBeforeLast('.').equals("cover", ignoreCase = true) && it.name.isImage() }
            ?.let { return it.name to share.read("$series/${it.name}") }
        val first = files.filter { it.isChapter && it.format != Format.UNSUPPORTED && !it.isEmptyFile }
            .minWithOrNull(compareBy(NATURAL_ORDER) { it.name })
            ?: return null
        val chapter = "$series/${first.name}"
        if (first.format == Format.EPUB) {
            val image = Epub.open(share, chapter)?.firstImage() ?: return null
            return image.name to share.readZipEntry(chapter, image).buffer()
        }
        val page = getPageList(SChapter.create().apply { url = chapter }).firstOrNull() ?: return null
        return open(page.imageUrl!!.toHttpUrl())
    }

    private fun createShare(settings: Settings): Share {
        val (address, username, password) = settings
        if (address.isSmbAddress()) {
            // Addresses copied from a file manager come percent-encoded
            val parts = address.replace('\\', '/').substringAfter("//").split('/').filter { it.isNotEmpty() }.map { it.percentDecoded() }
            if (parts.size < 2) throw Exception("Include the share name in the address: smb://server/share")
            val server = parts[0]
            val domain = username.substringBefore('\\', "")
            val user = username.substringAfter('\\')
            val auth = if (user.isEmpty()) AuthenticationContext.guest() else AuthenticationContext(user, password.toCharArray(), domain)
            return SmbShare(
                host = server.substringBefore(':'),
                port = server.substringAfter(':', "").toIntOrNull() ?: SmbShare.DEFAULT_PORT,
                shareName = parts[1],
                root = parts.drop(2),
                auth = auth,
            )
        }
        val url = address.toHttpUrlOrNull() ?: throw Exception("The address must start with smb://, http:// or https://")
        val root = url.newBuilder().query(null).fragment(null).apply {
            if (url.pathSegments.last().isEmpty()) removePathSegment(url.pathSize - 1)
        }.build()
        val headers = Headers.Builder().apply {
            if (username.isNotEmpty()) set("Authorization", Credentials.basic(username, password))
        }.build()
        return WebDavShare(client, root, headers)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_ADDRESS
            title = "Address"
            summary = address.ifEmpty { ADDRESS_HINT }
            dialogMessage = ADDRESS_HINT
            setOnPreferenceChangeListener { _, value ->
                val text = (value as String).trim()
                val valid = text.isEmpty() || text.isSmbAddress() || text.toHttpUrlOrNull() != null
                if (valid) {
                    summary = text.ifEmpty { ADDRESS_HINT }
                } else {
                    Toast.makeText(screen.context, "The address must start with smb://, http:// or https://", Toast.LENGTH_LONG).show()
                }
                valid
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_USERNAME
            title = "Username"
            summary = preferences.getString(PREF_USERNAME, "")!!.ifEmpty { "Empty for guest access" }
            setOnPreferenceChangeListener { _, value ->
                summary = (value as String).ifEmpty { "Empty for guest access" }
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Password"
            summary = "Hidden"
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)
    }

    private data class Settings(val address: String, val username: String, val password: String)

    private class ConfiguredShare(val settings: Settings, val share: Share)

    private enum class Format { FOLDER, ZIP, EPUB, UNSUPPORTED }

    private val RemoteFile.format get() = if (isDirectory) Format.FOLDER else name.format

    private val RemoteFile.isChapter get() = !name.isHidden() && format != null

    /** A 0-byte archive lists as a chapter but has nothing to read; WebDAV servers answer its range requests with 200 */
    private val RemoteFile.isEmptyFile get() = !isDirectory && size == 0L

    /** Dot files, and the folders NAS systems and Windows keep in shared folders */
    private fun String.isHidden() = startsWith('.') || lowercase() in SYSTEM_FOLDERS

    /** Valid `%XX` escapes decoded, a lone `%` kept */
    private fun String.percentDecoded() = HttpUrl.Builder().scheme("https").host("smb.invalid").addEncodedPathSegment(this).build().pathSegments.single()

    private fun String.isSmbAddress() = startsWith("smb://", ignoreCase = true) || startsWith("\\\\")

    private val String.format get() = when (substringAfterLast('.', "").lowercase()) {
        "zip", "cbz" -> Format.ZIP
        "epub" -> Format.EPUB
        "rar", "cbr", "7z", "cb7", "tar", "cbt" -> Format.UNSUPPORTED
        else -> null
    }

    private fun String.isImage() = substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** macOS resource forks that archivers add next to each file */
    private fun String.isJunk() = startsWith("__MACOSX/") || substringAfterLast('/').startsWith("._")

    private fun String.mimeType() = when (val extension = substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "heic" -> "image/heif"
        else -> "image/$extension"
    }.toMediaType()

    companion object {
        private const val PREF_ADDRESS = "address"
        private const val PREF_USERNAME = "username"
        private const val PREF_PASSWORD = "password"

        private const val ADDRESS_HINT = "smb://server/share/folder or https://server/webdav/folder"

        private const val HOST_SUFFIX = ".networkshare.invalid"
        private const val KIND_FILE = "file"
        private const val KIND_ENTRY = "entry"
        private const val KIND_COVER = "cover"

        private const val MEMO_SIZE = "size"
        private const val MEMO_MODIFIED = "modified"

        private const val METADATA_READS = 4

        private val SYSTEM_FOLDERS = setOf("@eadir", "#recycle", "#snapshot", "@recycle", "@recently-snapshot", "\$recycle.bin", "system volume information")

        private val IMAGE_EXTENSIONS = setOf("avif", "gif", "heif", "heic", "jpg", "jpeg", "jxl", "png", "webp", "jp2", "jpx")

        private val NATURAL_ORDER = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()
    }
}
