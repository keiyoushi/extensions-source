package eu.kanade.tachiyomi.extension.all.jellyfin

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import keiyoushi.utils.parseAs
import keiyoushi.zip.dataRange
import keiyoushi.zip.range
import keiyoushi.zip.rangeSource
import keiyoushi.zip.readEntry
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

// A book's pages: a CBZ's images read with range requests; a PDF, or another archive's images, from a copy in the app's cache
class PageReader(
    private val cacheDir: File,
    private val download: (id: String, target: File) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        val fileUrl = request.url.newBuilder().fragment(null).build()

        return when {
            fragment.startsWith(ZIP_PAGE) -> {
                val entry = fragment.removePrefix(ZIP_PAGE).parseAs<ZipPageDto>()
                val response = chain.proceed(request.newBuilder().url(fileUrl).range(entry.dataRange()).build())
                if (!response.isSuccessful) return response
                val image = readEntry(response.rangeSource(), entry.compressedSize, entry.method).buffer()
                response.newBuilder()
                    .code(200)
                    .message("OK")
                    .removeHeader("Content-Range")
                    .removeHeader("Content-Length")
                    .body(image.asResponseBody(entry.name.imageType()))
                    .build()
            }
            fragment.startsWith(FILE_PAGE) -> {
                val name = fragment.removePrefix(FILE_PAGE)
                val file = File(extractArchive(fileUrl.itemId).first, name)
                imageResponse(request, file.source().buffer(), name.imageType())
            }
            fragment.startsWith(PDF_PAGE) -> {
                imageResponse(request, renderPdfPage(localCopy(fileUrl.itemId, download), fragment.removePrefix(PDF_PAGE).toInt()), JPEG)
            }
            else -> chain.proceed(request)
        }
    }

    // The app resumes a stopped download with a range of its own unless the request already has one
    fun withZipRange(request: Request): Request {
        val fragment = request.url.fragment?.takeIf { it.startsWith(ZIP_PAGE) } ?: return request
        return request.newBuilder().range(fragment.removePrefix(ZIP_PAGE).parseAs<ZipPageDto>().dataRange()).build()
    }

    fun pdfPageCount(id: String): Int = localCopy(id, download).openPdf { it.pageCount }

    // The archive's images in page order, extracted under their page numbers
    fun archivePages(id: String): List<String> = extractArchive(id).second

    private fun extractArchive(id: String): Pair<File, List<String>> = localCopy(id) { _, target ->
        val archive = File(cacheDir, "$id.archive")
        download(id, archive)
        target.mkdirs()
        try {
            val names = mutableListOf<String>()
            readArchive(archive) { name, write ->
                if (name.isImage()) {
                    write(File(target, "${names.size}"))
                    names += name
                }
            }
            names.withIndex().sortedWith(compareBy(NATURAL_ORDER) { it.value }).forEachIndexed { page, (index, name) ->
                File(target, "$index").renameTo(File(target, "$page.${name.substringAfterLast('.').lowercase()}"))
            }
        } finally {
            archive.delete()
        }
    }.let { it to it.list()!!.sortedBy { name -> name.substringBefore('.').toInt() } }

    private val HttpUrl.itemId get() = pathSegments.let { it[it.size - 2] } // .../Items/{id}/File

    private fun cached(id: String) = File(cacheDir, id)

    // Kept until an hour after their last use, and made again when the app reads a page from a page list it kept longer
    private fun localCopy(id: String, create: (id: String, target: File) -> Unit): File {
        val copy = cached(id)
        if (!copy.exists()) {
            synchronized(this) {
                if (!copy.exists()) {
                    cacheDir.mkdirs()
                    val expired = now() - CACHE_LIFETIME.inWholeMilliseconds
                    cacheDir.listFiles()?.filter { it.lastModified() < expired }?.forEach { it.deleteRecursively() }

                    val part = File(cacheDir, "$id.part")
                    part.deleteRecursively()
                    try {
                        create(id, part)
                        part.renameTo(copy)
                    } finally {
                        part.deleteRecursively()
                    }
                }
            }
        }
        copy.setLastModified(now())
        return copy
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private fun readArchive(file: File, onEntry: (name: String, write: (File) -> Unit) -> Unit) {
        val archive = Archive.readNew()
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readOpenFileName(archive, file.absolutePath.toByteArray(), BLOCK_SIZE)
            while (true) {
                val entry = Archive.readNextHeader(archive).takeIf { it != 0L } ?: break
                if (ArchiveEntry.filetype(entry) != ArchiveEntry.AE_IFREG) continue
                val name = ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.decodeToString() ?: continue
                onEntry(name) { target ->
                    ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY).use {
                        Archive.readDataIntoFd(archive, it.fd)
                    }
                }
            }
        } finally {
            Archive.readFree(archive)
        }
    }

    private fun renderPdfPage(pdf: File, index: Int): Buffer {
        val image = Buffer()
        pdf.openPdf { renderer ->
            renderer.openPage(index).use { page ->
                val scale = minOf(MAX_SCALE, MAX_WIDTH / page.width)
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, image.outputStream())
                bitmap.recycle()
            }
        }
        return image
    }

    private fun <T> File.openPdf(block: (PdfRenderer) -> T): T {
        val descriptor = ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY)
        return descriptor.use { PdfRenderer(it).use(block) }
    }

    private fun imageResponse(request: Request, body: BufferedSource, type: MediaType) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.asResponseBody(type))
        .build()
}

private fun ZipPageDto.dataRange() = dataRange(offset, compressedSize, localHeaderAllowance)

const val ZIP_PAGE = "zip:"
const val FILE_PAGE = "file:"
const val PDF_PAGE = "pdf:"

private val IMAGE_TYPES = mapOf(
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "avif" to "image/avif",
    "jxl" to "image/jxl",
    "heif" to "image/heif",
    "heic" to "image/heic",
    "bmp" to "image/bmp",
)

private val String.extension get() = substringAfterLast('.', "").lowercase()

// Images, leaving out macOS resource forks and hidden files
fun String.isImage() = extension in IMAGE_TYPES && split('/').none { it.startsWith('.') || it == "__MACOSX" }

private fun String.imageType() = IMAGE_TYPES.getValue(extension).toMediaType()

val NATURAL_ORDER: Comparator<String> = CaseInsensitiveSimpleNaturalComparator.getInstance()

private val CACHE_LIFETIME = 1.hours
private const val BLOCK_SIZE = 10240L
private const val MAX_WIDTH = 2000f
private const val MAX_SCALE = 3f
private val JPEG = "image/jpeg".toMediaType()
