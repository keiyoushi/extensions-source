package eu.kanade.tachiyomi.extension.ja.readerstore

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import keiyoushi.utils.readIntLittleEndian
import okhttp3.Headers
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val fragment = url.fragment

        if (fragment.isNullOrEmpty() || !url.pathSegments.last().contains(PATH_IMAGE_URL)) {
            return chain.proceed(request)
        }

        val parts = fragment.split(";")
        val (index, nmr, token, uuid, maxIndexValue) = parts
        val pageIndex = index.toInt()
        val maxIndex = maxIndexValue.toInt()
        val cipherKey = parts[5]
        val isComic = parts[6] == TYPE_COMIC
        val contentId = url.pathSegments[1]

        val authHeaders = request.headers.newBuilder()
            .set(HEADER_NMR, nmr)
            .set(HEADER_TOKEN, token)
            .set(HEADER_UUID, uuid)
            .set(HEADER_USE_CACHE, "false")
            .build()

        val imageResponse = chain.proceed(
            Request.Builder()
                .url(url)
                .headers(authHeaders)
                .addPageHeaders(listOf(pageIndex), maxIndex)
                .build(),
        ).parseAs<ImageResponse>()

        val cdnUrl = imageResponse.data.url.toHttpUrl()
        val batchIndices = cdnUrl.queryParameter(PARAM_INDICES)
            ?.split(",")?.map(String::toInt)
            ?: listOf(pageIndex)
        val targetOffset = batchIndices.indexOf(pageIndex).coerceAtLeast(0)
        val meta = imageResponse.data.meta[targetOffset]
        val envV = cdnUrl.queryParameter("v")?.toInt() ?: 1

        val rawImage = chain.proceed(
            Request.Builder()
                .url(cdnUrl)
                .headers(authHeaders)
                .addPageHeaders(batchIndices, maxIndex, includeExcludeRanges = true)
                .build(),
        ).use { it.body.source().readImagePage(targetOffset) }

        val image: ByteArray
        val order: List<Int>
        val sideLength: Int

        if (meta.isCrypted) {
            val headerKey = decodeCipherKey(cipherKey)
            val encryptedHeader = fetchEncryptedHeader(chain, authHeaders, contentId, batchIndices, maxIndex, targetOffset)

            val decoder = Decoder()
            val decryptedHeader = decoder.decrypt(headerKey, CIPHER_IV, encryptedHeader)
            val keyBuffer = ByteBuffer.wrap(decryptedHeader, IMAGE_KEY_OFFSET, IMAGE_KEY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val imageKey = IntArray(4) { keyBuffer.int }

            val (resolvedSide, resolvedOrder) = parseScrambleOrder(decryptedHeader)
            sideLength = resolvedSide
            order = resolvedOrder
            image = decoder.decrypt(imageKey, CIPHER_IV, rawImage)
        } else {
            if (meta.isScrambled) {
                val (resolvedSide, resolvedOrder) = computeScrambleOrder(meta, contentId, isComic, envV)
                sideLength = resolvedSide
                order = resolvedOrder
            } else {
                sideLength = 0
                order = emptyList()
            }
            image = rawImage
        }

        if (!meta.isScrambled || order.isEmpty()) {
            return Buffer().write(image).toImageResponse(request, meta.mimetype.toMediaType())
        }

        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
        val unscrambled = unscramble(bitmap, order, sideLength, meta)
        bitmap.recycle()
        val buffer = Buffer()
        unscrambled.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        unscrambled.recycle()

        return buffer.toImageResponse(request, WEBP_MEDIA_TYPE)
    }

    private fun decodeCipherKey(hex: String): IntArray {
        require(hex.length == 32) { "Invalid decryption key" }
        val buffer = ByteBuffer.wrap(hex.decodeHex())
        return IntArray(4) { buffer.int }
    }

    private fun fetchEncryptedHeader(
        chain: Interceptor.Chain,
        authHeaders: Headers,
        contentId: String,
        batchIndices: List<Int>,
        maxIndex: Int,
        targetOffset: Int,
    ): ByteArray {
        val headerUrl = "$VIEWER_URL/$contentId/header".toHttpUrl().newBuilder()
            .addQueryParameter(PARAM_INDICES, batchIndices.joinToString(","))
            .addQueryParameter(PARAM_CODE, QUALITY_HIGH)
            .addQueryParameter(PARAM_ACCEPT, ACCEPT_FORMATS)
            .build()

        return chain.proceed(
            Request.Builder()
                .url(headerUrl)
                .headers(authHeaders)
                .addPageHeaders(batchIndices, maxIndex, includeExcludeRanges = true)
                .build(),
        ).use { it.body.source().readHeaderPage(targetOffset) }
    }

    private fun Request.Builder.addPageHeaders(
        indices: List<Int>,
        maxIndex: Int,
        includeExcludeRanges: Boolean = false,
    ) = apply {
        addHeader("X-Indices", "[${indices.joinToString(",")}]")
        addHeader("X-Max-Index", maxIndex.toString())
        addHeader("X-Quality", QUALITY_HIGH)
        if (includeExcludeRanges) addHeader("X-Exclude-Ranges", "[]")
    }

    private fun Buffer.toImageResponse(request: Request, mediaType: MediaType): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_2)
        .code(200)
        .message("OK")
        .body(asResponseBody(mediaType, size))
        .build()

    // [u32 LE pageSize][pageBytes] frames
    private fun BufferedSource.readImagePage(targetOffset: Int): ByteArray {
        repeat(targetOffset) { skip(readIntLe().toLong()) }
        return readByteArray(readIntLe().toLong())
    }

    // /header stream: [u32 metaSize][metaJson][u32 pageSize][serializablePage]
    // inside page [u32 headerSize][20-byte MAC][encryptedHeader] ending at headerSize + 4
    private fun BufferedSource.readHeaderPage(targetOffset: Int): ByteArray {
        repeat(targetOffset) {
            skip(readIntLe().toLong())
            skip(readIntLe().toLong())
        }
        skip(readIntLe().toLong())
        return sliceEncryptedHeader(readByteArray(readIntLe().toLong()))
    }

    private fun sliceEncryptedHeader(page: ByteArray): ByteArray {
        val start = HEADER_SIZE_PREFIX + MAC_SIZE
        val end = page.readIntLittleEndian(0) + HEADER_SIZE_PREFIX
        return page.copyOfRange(start, end)
    }

    private fun parseScrambleOrder(decryptedHeader: ByteArray): Pair<Int, List<Int>> {
        if (decryptedHeader.size < SCRAMBLE_ORDER_OFFSET + 4) return 0 to emptyList()

        val buffer = ByteBuffer.wrap(decryptedHeader, SCRAMBLE_ORDER_OFFSET, decryptedHeader.size - SCRAMBLE_ORDER_OFFSET).order(ByteOrder.LITTLE_ENDIAN)
        val tileCount = (buffer.short.toInt() and 0xFFFF) / 2
        val sideLength = buffer.short.toInt() and 0xFFFF
        val safeCount = minOf(tileCount, buffer.remaining() / 2)
        val order = ArrayList<Int>(safeCount)
        repeat(safeCount) { order.add(buffer.short.toInt() and 0xFFFF) }
        return sideLength to order
    }

    private fun computeScrambleOrder(meta: Meta, contentId: String, isComic: Boolean, envV: Int): Pair<Int, List<Int>> {
        val sideLength = if (meta.mimetype == MIME_WEBP && isComic) 152 else 48
        val width = meta.width
        val height = meta.height
        var hBlocks = width / sideLength
        var vBlocks = height / sideLength
        if (width % sideLength != 0) hBlocks++
        if (height % sideLength != 0) vBlocks++

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

        return Pair(sideLength, result)
    }

    private fun shuffle(indices: List<Int>, contentId: String, envV: Int): List<Int> {
        val preShared = intArrayOf(19, 20, 14, 1, 5, 2, 4, 15, 9, 17, 8, 16, 18, 11, 10, 7, 12, 6, 13, 3)
        val seeds = contentId.mapNotNull { if (it in '1'..'9') it - '0' else null }
        if (seeds.isEmpty()) return indices
        var pi = envV % seeds.size
        var si = 0
        return indices
            .map { idx -> (preShared[si++ % preShared.size] + seeds[pi++ % seeds.size]) to idx }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.second }
    }

    private fun unscramble(bitmap: Bitmap, order: List<Int>, sideLength: Int, meta: Meta): Bitmap {
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
        return result
    }

    private companion object {
        // /header framing: [u32 headerSize][MAC][encryptedHeader]; header ends at headerSize + 4
        const val HEADER_SIZE_PREFIX = 4
        const val MAC_SIZE = 20

        // Decrypted header layout: [4 bytes][16-byte image key][scramble order]
        const val IMAGE_KEY_OFFSET = 4
        const val IMAGE_KEY_SIZE = 16
        const val SCRAMBLE_ORDER_OFFSET = IMAGE_KEY_OFFSET + IMAGE_KEY_SIZE

        const val MIME_WEBP = "image/webp"
        const val TYPE_COMIC = "comic"

        val CIPHER_IV = intArrayOf(0, 1, 2, 3)
        val WEBP_MEDIA_TYPE = MIME_WEBP.toMediaType()
    }
}
