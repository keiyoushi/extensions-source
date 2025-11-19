package eu.kanade.tachiyomi.extension.ja.comicgrast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        val seed = url.queryParameter("seed")
        val sizeParam = url.queryParameter("size")

        if (seed.isNullOrEmpty() || sizeParam.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        val body = response.body
        val bytes = body.bytes()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val sliceSize = max(bitmap.width, bitmap.height) / sizeParam.toFloat()

        val result = unscrambleImg(bitmap, sliceSize, seed)

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)
        val resultBytes = output.toByteArray()

        return response.newBuilder()
            .body(resultBytes.toResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    private fun unscrambleImg(img: Bitmap, sliceSize: Float, seed: String): Bitmap {
        val width = img.width
        val height = img.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val slices = getSlices(width, height, sliceSize)

        for (group in slices.values) {
            val rng = SeedRandom(seed)

            val len = group.size
            val indices = MutableList(len) { it }
            val shuffleInd = shuffle(indices, rng)
            val cols = getColsInGroup(group)

            val groupX = group[0].x
            val groupY = group[0].y

            for (i in 0 until len) {
                val destSlice = group[i]
                val s = shuffleInd[i]

                val row = s / cols
                val col = s % cols

                val sliceW = destSlice.width
                val sliceH = destSlice.height

                val srcX = groupX + col * sliceW
                val srcY = groupY + row * sliceH

                val srcRect = Rect(srcX, srcY, srcX + sliceW, srcY + sliceH)
                val dstRect = Rect(destSlice.x, destSlice.y, destSlice.x + sliceW, destSlice.y + sliceH)

                canvas.drawBitmap(img, srcRect, dstRect, null)
            }
        }
        return result
    }

    private class Slice(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun getSlices(imgWidth: Int, imgHeight: Int, sliceSize: Float): Map<String, List<Slice>> {
        val totalParts = (ceil(imgWidth / sliceSize) * ceil(imgHeight / sliceSize)).toInt()
        val verticalSlices = ceil(imgWidth / sliceSize).toInt()
        val groups = LinkedHashMap<String, MutableList<Slice>>()

        for (i in 0 until totalParts) {
            val row = floor(i.toDouble() / verticalSlices).toInt()
            val col = i - row * verticalSlices

            val x = (col * sliceSize).toInt()
            val y = (row * sliceSize).toInt()

            val w = if (x + sliceSize <= imgWidth) sliceSize.toInt() else imgWidth - x
            val h = if (y + sliceSize <= imgHeight) sliceSize.toInt() else imgHeight - y

            val key = "$w-$h"
            val list = groups.getOrPut(key) { ArrayList() }
            list.add(Slice(x, y, w, h))
        }
        return groups
    }

    private fun getColsInGroup(slices: List<Slice>): Int {
        if (slices.size == 1) return 1
        val firstY = slices[0].y
        for (i in slices.indices) {
            if (slices[i].y != firstY) return i
        }
        return slices.size
    }

    private fun shuffle(arr: MutableList<Int>, rng: SeedRandom): List<Int> {
        val size = arr.size
        val resp = ArrayList<Int>(size)
        val keys = ArrayList<Int>(size)
        for (i in 0 until size) keys.add(i)

        repeat(size) {
            val r = floor(rng.nextDouble() * keys.size).toInt()
            val g = keys[r]
            keys.removeAt(r)
            resp.add(arr[g])
        }
        return resp
    }

    private class SeedRandom(seed: String) {
        private val width = 256
        private val chunks = 6
        private val digits = 52
        private val startdenom = width.toDouble().pow(chunks.toDouble())
        private val significance = 2.0.pow(digits.toDouble())
        private val overflow = significance * 2
        private val mask = width - 1
        private val arc4: ARC4

        init {
            val key = mixkey(seed, width, mask)
            arc4 = ARC4(key, width, mask)
        }

        fun nextDouble(): Double {
            var n = arc4.g(chunks).toDouble()
            var d = startdenom
            var x = 0L
            while (n < significance) {
                n = (n + x) * width
                d *= width
                x = arc4.g(1)
            }
            while (n >= overflow) {
                n /= 2
                d /= 2
                x = x ushr 1
            }
            return (n + x) / d
        }

        private fun mixkey(seed: String, width: Int, mask: Int): IntArray {
            val key = IntArray(width)
            val stringseed = seed + ""
            var smear = 0
            var j = 0
            while (j < stringseed.length) {
                val charCode = stringseed[j].code
                val keyVal = key[mask and j]
                smear = smear xor (keyVal * 19)
                val mixed = mask and (smear + charCode)
                key[mask and j] = mixed
                j++
            }
            val actualLen = if (stringseed.isEmpty()) 0 else if (stringseed.length < width) stringseed.length else width
            return key.copyOfRange(0, actualLen)
        }
    }

    private class ARC4(key: IntArray, val width: Int, val mask: Int) {
        private var i = 0
        private var j = 0
        private val s = IntArray(width)

        init {
            var effectiveKey = key
            var keylen = effectiveKey.size

            if (keylen == 0) {
                effectiveKey = intArrayOf(0)
                keylen = 1
            }

            for (k in 0 until width) s[k] = k

            var jCounter = 0
            for (k in 0 until width) {
                val t = s[k]
                jCounter = mask and (jCounter + effectiveKey[k % keylen] + t)
                s[k] = s[jCounter]
                s[jCounter] = t
            }
            g(width)
        }

        fun g(count: Int): Long {
            var r = 0L
            var c = count
            while (c-- > 0) {
                i = mask and (i + 1)
                val t = s[i]
                j = mask and (j + t)
                val sj = s[j]
                s[i] = sj
                s[j] = t
                r = r * width + s[mask and (sj + t)]
            }
            return r
        }
    }
}
