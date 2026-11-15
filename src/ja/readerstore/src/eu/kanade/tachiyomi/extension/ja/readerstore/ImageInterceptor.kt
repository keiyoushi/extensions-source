package eu.kanade.tachiyomi.extension.ja.readerstore

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import keiyoushi.utils.readIntBigEndian
import keiyoushi.utils.readIntLittleEndian
import keiyoushi.utils.readUShortLittleEndian
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.BufferedSource
import kotlin.math.ceil

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val fragment = url.fragment

        if (fragment.isNullOrEmpty() || PATH_IMAGE_URL !in url.pathSegments.last()) {
            return chain.proceed(request)
        }

        val parts = fragment.split(";")
        val (nmr, token, uuid, maxIndex, cipherKey) = parts
        val isComic = parts[5] == TYPE_COMIC
        val pageIndex = url.queryParameter(PARAM_INDICES)!!
        val contentId = url.pathSegments[1]

        val viewerHeaders = request.headers.newBuilder()
            .set(HEADER_NMR, nmr)
            .set(HEADER_TOKEN, token)
            .set(HEADER_UUID, uuid)
            .set(HEADER_USE_CACHE, "false")
            .set(HEADER_INDICES, "[$pageIndex]")
            .set(HEADER_MAX_INDEX, maxIndex)
            .set(HEADER_QUALITY, QUALITY_HIGH)
            .set(HEADER_EXCLUDE_RANGES, "[]")
            .build()

        val imageResponse = chain.proceed(
            request.newBuilder().headers(viewerHeaders).build(),
        ).parseAs<ImageResponse>()

        val cdnUrl = imageResponse.data.url.toHttpUrl()
        val meta = imageResponse.data.meta.first()

        val frame = chain.proceed(
            Request.Builder().url(cdnUrl).headers(viewerHeaders).build(),
        ).use { it.body.source().readFrame() }

        if (!meta.isCrypted) {
            if (!meta.isScrambled) {
                return frame.toImageResponse(request, meta.mimetype.toMediaType())
            }

            val envV = cdnUrl.queryParameter(PARAM_ENV)?.toInt() ?: 1
            val (sideLength, order) = computeScrambleOrder(meta, contentId, isComic, envV)
            return frame.unscramble(order, sideLength, meta).toImageResponse(request)
        }

        val headerUrl = "$VIEWER_URL/$contentId/header".toHttpUrl().newBuilder()
            .addQueryParameter(PARAM_INDICES, pageIndex)
            .addQueryParameter(PARAM_CODE, QUALITY_HIGH)
            .addQueryParameter(PARAM_ACCEPT, ACCEPT_FORMATS)
            .build()

        val encryptedHeader = chain.proceed(
            Request.Builder().url(headerUrl).headers(viewerHeaders).build(),
        ).use { it.body.source().readEncryptedHeader() }

        val decoder = Decoder()
        val decryptedHeader = decoder.decrypt(cipherKey.toCipherKey(), CIPHER_IV, encryptedHeader)
        val imageKey = IntArray(4) { decryptedHeader.readIntLittleEndian(IMAGE_KEY_OFFSET + it * 4) }
        val (sideLength, order) = decryptedHeader.parseScrambleOrder()
        val image = decoder.decrypt(imageKey, CIPHER_IV, frame.readByteArray())

        if (!meta.isScrambled || order.isEmpty()) {
            return Buffer().write(image).toImageResponse(request, meta.mimetype.toMediaType())
        }

        return Buffer().write(image).unscramble(order, sideLength, meta).toImageResponse(request)
    }

    private fun String.toCipherKey(): IntArray {
        require(length == 32) { "Invalid decryption key" }
        val bytes = decodeHex()
        return IntArray(4) { bytes.readIntBigEndian(it * 4) }
    }

    // CDN image stream: [u32 LE size][page bytes]
    private fun BufferedSource.readFrame(): Buffer {
        val size = readIntLe().toLong()
        val buffer = Buffer()
        buffer.write(this, size)
        return buffer
    }

    // header stream: [u32 metaSize][metaJson][u32 pageSize][page]
    // where page is [u32 headerSize][20-byte MAC][encryptedHeader] and the header ends at headerSize + 4
    private fun BufferedSource.readEncryptedHeader(): ByteArray {
        skip(readIntLe().toLong())
        skip(PREFIX_SIZE.toLong())
        val headerSize = readIntLe()
        skip(MAC_SIZE.toLong())
        return readByteArray((headerSize - MAC_SIZE).toLong())
    }

    // Decrypted header layout: [4 bytes][16-byte image key][u16 tileCount*2][u16 side][u16 order]
    private fun ByteArray.parseScrambleOrder(): Pair<Int, List<Int>> {
        if (size < SCRAMBLE_ORDER_OFFSET + 4) return 0 to emptyList()

        val tileCount = readUShortLittleEndian(SCRAMBLE_ORDER_OFFSET) / 2
        val sideLength = readUShortLittleEndian(SCRAMBLE_ORDER_OFFSET + 2)
        val orderOffset = SCRAMBLE_ORDER_OFFSET + 4
        val safeCount = minOf(tileCount, (size - orderOffset) / 2)
        return sideLength to List(safeCount) { readUShortLittleEndian(orderOffset + it * 2) }
    }

    private fun computeScrambleOrder(meta: Meta, contentId: String, isComic: Boolean, envV: Int): Pair<Int, List<Int>> {
        val sideLength = if (meta.mimetype == MIME_WEBP && isComic) 152 else 48
        var hBlocks = meta.width / sideLength
        var vBlocks = meta.height / sideLength
        if (meta.width % sideLength != 0) hBlocks++
        if (meta.height % sideLength != 0) vBlocks++

        val mainIndices = mutableListOf<Int>()
        val hTailIndices = mutableListOf<Int>()
        for (i in 0 until vBlocks - 1) {
            val line = (i * hBlocks until (i + 1) * hBlocks).toList()
            mainIndices.addAll(line.dropLast(1))
            hTailIndices.add(line.last())
        }
        val vTailIndices = ((vBlocks - 1) * hBlocks until vBlocks * hBlocks - 1).toMutableList()

        val shuffledMain = shuffle(mainIndices, contentId, envV)
        val shuffledHTail = shuffle(hTailIndices, contentId, envV)
        val shuffledVTail = shuffle(vTailIndices, contentId, envV)

        val result = mutableListOf<Int>()
        var mainOffset = 0
        for (i in 0 until vBlocks - 1) {
            result.addAll(shuffledMain.subList(mainOffset, mainOffset + hBlocks - 1))
            mainOffset += hBlocks - 1
            result.add(shuffledHTail[i])
        }
        result.addAll(shuffledVTail)
        result.add(hBlocks * vBlocks - 1)

        return sideLength to result
    }

    private fun shuffle(indices: List<Int>, contentId: String, envV: Int): List<Int> {
        val seeds = contentId.mapNotNull { if (it in '1'..'9') it - '0' else null }
        if (seeds.isEmpty()) return indices
        var pi = envV % seeds.size
        var si = 0
        return indices
            .map { idx -> (PRE_SHARED[si++ % PRE_SHARED.size] + seeds[pi++ % seeds.size]) to idx }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.second }
    }

    private fun Buffer.unscramble(order: List<Int>, sideLength: Int, meta: Meta): Bitmap {
        val bitmap = inputStream().use { BitmapFactory.decodeStream(it) }
        val srcW = bitmap.width
        val srcH = bitmap.height
        val dstW = meta.width
        val dstH = meta.height

        val srcRect = Rect()
        val dstRect = Rect()
        val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val cols = ceil(dstW.toDouble() / sideLength).toInt()
        val rows = ceil(dstH.toDouble() / sideLength).toInt()

        val lastColW = if (dstW % sideLength != 0) dstW % sideLength else sideLength
        val lastRowH = if (dstH % sideLength != 0) dstH % sideLength else sideLength
        val lastCol = cols - 1
        val lastRow = rows - 1

        val colPad = if (srcW == dstW) 0f else (srcW - dstW).toFloat() / lastCol
        val rowPad = if (srcH == dstH) 0f else (srcH - dstH).toFloat() / lastRow

        for (srcTile in order.indices) {
            val dstTile = order[srcTile]

            val srcCol = srcTile % cols
            val srcRow = srcTile / cols
            val dstCol = dstTile % cols
            val dstRow = dstTile / cols

            val tileW = if (dstCol == lastCol) lastColW else sideLength
            val tileH = if (dstRow == lastRow) lastRowH else sideLength

            val sx = ((sideLength + colPad) * srcCol).toInt()
            val sy = ((sideLength + rowPad) * srcRow).toInt()
            val dx = sideLength * dstCol
            val dy = sideLength * dstRow

            srcRect.set(sx, sy, sx + tileW, sy + tileH)
            dstRect.set(dx, dy, dx + tileW, dy + tileH)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        bitmap.recycle()
        return result
    }

    private fun Bitmap.toImageResponse(request: Request): Response {
        val buffer = Buffer()
        compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        recycle()
        return buffer.toImageResponse(request, WEBP_MEDIA_TYPE)
    }

    private fun Buffer.toImageResponse(request: Request, mediaType: MediaType): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_2)
        .code(200)
        .message("OK")
        .body(asResponseBody(mediaType, size))
        .build()

    private companion object {
        const val PREFIX_SIZE = 4
        const val MAC_SIZE = 20
        const val IMAGE_KEY_OFFSET = 4
        const val IMAGE_KEY_SIZE = 16
        const val SCRAMBLE_ORDER_OFFSET = IMAGE_KEY_OFFSET + IMAGE_KEY_SIZE
        const val MIME_WEBP = "image/webp"
        const val TYPE_COMIC = "comic"

        val CIPHER_IV = intArrayOf(0, 1, 2, 3)
        val PRE_SHARED = intArrayOf(19, 20, 14, 1, 5, 2, 4, 15, 9, 17, 8, 16, 18, 11, 10, 7, 12, 6, 13, 3)
        val WEBP_MEDIA_TYPE = MIME_WEBP.toMediaType()
    }
}
