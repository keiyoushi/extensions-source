package eu.kanade.tachiyomi.lib.publus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.nio.charset.StandardCharsets
import kotlin.math.floor

@Serializable
class PublusFragment(
    val file: String,
    val no: Int,
    val ns: Long,
    val ps: Long,
    val rs: Long,
    val bw: Int, // Block Width
    val bh: Int, // Block Height
    val cw: Int, // Content Width
    val ch: Int, // Content Height
    val k1: List<Int>,
    val k2: List<Int>,
    val k3: List<Int>,
)

class PublusPage(
    val index: Int,
    val filename: String,
    val no: Int,
    val ns: Long,
    val ps: Long,
    val rs: Long,
    val blockWidth: Int,
    val blockHeight: Int,
    val width: Int,
    val height: Int,
)

class PublusPageAttributes(
    val no: Int,
    val ns: Long,
    val ps: Long,
    val rs: Long,
    val blockWidth: Int,
    val blockHeight: Int,
    val contentWidth: Int,
    val contentHeight: Int,
)

object Publus {

    /**
     * @param pages The list of pages.
     * @param keys The decryption keys (k1, k2, k3) obtained from the [Decoder].
     * @param imageUrlPrefix The content URL for the images.
     */
    fun generatePages(
        pages: List<PublusPage>,
        keys: List<IntArray>,
        imageUrlPrefix: String
    ): List<Page> {
        val k1 = keys[0].toList()
        val k2 = keys[1].toList()
        val k3 = keys[2].toList()

        return pages.map {
            val filename = PublusImage.generateFilename(it.filename, keys)
            val imgUrl = imageUrlPrefix + filename

            val fragmentData = PublusFragment(
                file = it.filename,
                no = it.no,
                ns = it.ns,
                ps = it.ps,
                rs = it.rs,
                bw = it.blockWidth,
                bh = it.blockHeight,
                cw = it.width,
                ch = it.height,
                k1 = k1,
                k2 = k2,
                k3 = k3
            )

            val fragmentJson = fragmentData.toJsonString()
            val fragment = Base64.encodeToString(fragmentJson.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

            Page(it.index, imageUrl = "$imgUrl#$fragment")
        }
    }

    class PublusInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url

            if (url.fragment.isNullOrEmpty()) {
                return chain.proceed(request)
            }

            val response = chain.proceed(request)

            val fragmentJson = String(
                Base64.decode(url.fragment, Base64.URL_SAFE),
                StandardCharsets.UTF_8
            )
            val params = fragmentJson.parseAs<PublusFragment>()

            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())

            val keys = listOf(
                params.k1.toIntArray(),
                params.k2.toIntArray(),
                params.k3.toIntArray()
            )

            val attributes = PublusPageAttributes(
                no = params.no,
                ns = params.ns,
                ps = params.ps,
                rs = params.rs,
                blockWidth = params.bw,
                blockHeight = params.bh,
                contentWidth = params.cw,
                contentHeight = params.ch,
            )

            val unscrambled = PublusImage.unscramble(bitmap, attributes, keys, params.file)
            bitmap.recycle()
            val buffer = Buffer()
            unscrambled.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
            unscrambled.recycle()

            return response.newBuilder()
                .body(buffer.asResponseBody("image/jpeg".toMediaType()))
                .build()
        }
    }

    class Decoder(private val data: String, private val filename: String = "configuration_pack.json") {
        fun decode(): DecodedResult {
            val filenameKey = filename.toByteArray(StandardCharsets.UTF_8).map { it.toInt() and 0xFF }.toIntArray()

            val state = mod9Base64Decode(data)

            mod14Mutate(0, state)
            mod6XorLoop(filenameKey, state) // B0p
            mod10ShuffleA(filenameKey, state) // A7L
            mod12ShuffleB(filenameKey, state) // A6I
            mod15Mix(state) // A2F
            mod7UpdateKeys(filenameKey, state) // B0L
            mod14Mutate(1, state)
            mod14Mutate(2, state)
            mod14Mutate(3, state)
            mod4DecryptLoop(filenameKey, state) // tB0l

            val resultStr = mod13Utf8Decode(state.payload, state.length)
            return DecodedResult(resultStr, listOf(state.key1, state.key2, state.key3))
        }

        class DecodedResult(val json: String, val keys: List<IntArray>)

        private class State(
            var payload: IntArray,
            var length: Int,
            var key1: IntArray,
            var key2: IntArray,
            var key3: IntArray
        )

        // Modules
        private fun mod9Base64Decode(dataStr: String): State {
            val b64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val b64Map = IntArray(256) { -1 }
            for (i in b64Chars.indices) b64Map[b64Chars[i].code] = i

            val key1 = IntArray(32)
            val key2 = IntArray(32)
            val key3 = IntArray(32)

            var currentTarget = key1
            var dIdx = 0
            var s = 0

            // Header Parsing
            while (s < 128) {
                if (s >= dataStr.length) break
                val c1 = b64Map[dataStr[s++].code]; val c2 = b64Map[dataStr[s++].code]
                val c3 = b64Map[dataStr[s++].code]; val c4 = b64Map[dataStr[s++].code]

                val b1 = (c1 shl 2) or (c2 shr 4)
                val b2 = ((c2 and 15) shl 4) or (c3 shr 2)
                val b3 = ((c3 and 3) shl 6) or c4

                currentTarget[dIdx++] = b1
                if (s == 88) { currentTarget = key3; dIdx = 0 }
                currentTarget[dIdx++] = b2
                if (s == 44) { currentTarget = key2; dIdx = 0 }
                currentTarget[dIdx++] = b3
            }

            // Body Parsing
            val payloadStr = dataStr.substring(128)
            val payloadBytes = Base64.decode(payloadStr, Base64.DEFAULT)
            val payloadInts = payloadBytes.map { it.toInt() and 0xFF }.toIntArray()

            return State(payloadInts, payloadInts.size, key1, key2, key3)
        }

        private fun mod14Mutate(mode: Int, state: State) {
            val (l, v, h, uKey, p) = when(mode) {
                3 -> listOf(state.key1, 32, state.key2, state.key3, null)
                2 -> listOf(state.key2, 32, state.key1, state.key3, null)
                1 -> listOf(state.key3, 32, state.key1, state.key2, null)
                else -> listOf(state.payload, state.length, state.key1, state.key2, state.key3)
            }

            val lArr = l as IntArray
            val vInt = v as Int
            val hArr = h as IntArray
            val uArr = uKey as IntArray
            val pArr = p as? IntArray

            var w = 0; var A = 0
            fun nFunc(arr: IntArray) { for (a in 0 until 32) { w = (w + arr[a]) and 255; A = A xor arr[a] } }

            nFunc(hArr); nFunc(uArr); if (pArr != null) nFunc(pArr)

            val kFlag = (w and 2) == 0; val yFlag = (w and 4) == 0; val mFlag = (w and 8) == 0
            val m = (A shr 5) and 0x7; val C = 8 - m

            var g = 0; val O = IntArray(32)

            fun swap(arr: IntArray, i: Int, j: Int) { val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp }
            fun uFuncBlockSwap(start: Int, end: Int, arr: IntArray) {
                var n = start; var t = end
                while (t > start) { swap(arr, n, t); t--; n-- }
            }

            while (g < vInt) {
                var E = g + 32; val S = E > vInt
                val j = if (S) { E = vInt; E - g } else 32

                var PVal = w; var UVal = A

                for (q in 0 until j) {
                    var x = lArr[g + q]
                    if (kFlag) x = ((85 and x) shl 1) or ((x shr 1) and 85)
                    if (yFlag) x = ((51 and x) shl 2) or ((x shr 2) and 51)
                    if (mFlag) x = ((15 and x) shl 4) or ((x shr 4) and 15)
                    O[q] = x
                    PVal = (PVal + x) and 255
                    UVal = UVal xor x
                }

                for (K in 0 until j) {
                    for (R in 1..6) {
                        val D = 1 shl R
                        if ((K and (D - 1)) != (D - 1)) break
                        if ((PVal and D) != D) {
                            val startSwap = K - (1 shl (R - 1))
                            uFuncBlockSwap(startSwap, K, O)
                        }
                    }
                }

                var J = UVal shr 3
                if (S) J %= j else J = J and 31

                if (m == 0) {
                    var KIdx = j - J
                    if (KIdx == j) KIdx = 0
                    for (R in g until E) {
                        lArr[R] = O[KIdx]
                        KIdx++; if (KIdx == j) KIdx = 0
                    }
                } else {
                    var KIdx = j - J - 1
                    for (R in g until E) {
                        var x = (O[KIdx] shl C) and 0xFF
                        KIdx++; if (KIdx == j) KIdx = 0
                        x = x or (O[KIdx] shr m)
                        lArr[R] = x and 255
                    }
                }
                g = E
            }
        }

        private fun swap(arr: IntArray, i: Int, j: Int) { val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp }

        private fun rc4Ksa(keyArr: IntArray): IntArray {
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0 until 256) {
                j = (j + s[i] + keyArr[i % keyArr.size]) % 256
                swap(s, i, j)
            }
            return s
        }

        private fun vQmi(vararg arrays: IntArray): IntArray {
            var size = 0; arrays.forEach { size += it.size }
            val combined = IntArray(size)
            var pos = 0; arrays.forEach { System.arraycopy(it, 0, combined, pos, it.size); pos += it.size }
            return rc4Ksa(combined)
        }

        private fun vSmi(e: IntArray, t: IntArray, r: IntArray, n: IntArray): IntArray {
            val combined = IntArray(t.size + r.size + n.size)
            var pos = 0
            System.arraycopy(t, 0, combined, pos, t.size); pos += t.size
            System.arraycopy(r, 0, combined, pos, r.size); pos += r.size
            System.arraycopy(n, 0, combined, pos, n.size)
            return mod11XorStream(e, combined)
        }

        private fun mod11XorStream(keyToUpdate: IntArray, inputStream: IntArray): IntArray {
            val sBox = rc4Ksa(inputStream)
            val res = IntArray(keyToUpdate.size)
            var i = 0; var j = 0
            for (idx in keyToUpdate.indices) {
                val b = keyToUpdate[idx]
                i = (i + 1) % 256
                j = (j + sBox[i]) % 256
                swap(sBox, i, j)
                val k = sBox[(sBox[i] + sBox[j]) % 256]
                res[idx] = b xor k
            }
            return res
        }

        private fun mod6XorLoop(fnKey: IntArray, state: State) {
            val sBox = vQmi(state.key2, fnKey, state.key3)
            for (s in 0 until state.length) state.payload[s] = state.payload[s] xor sBox[s % 256]
        }

        private fun mod10ShuffleA(fnKey: IntArray, state: State) {
            val sIters = (state.length or 1) - 2
            val sBox = vQmi(fnKey, state.key1, state.key2)
            var i = 0; var j = 0
            for (k in sIters downTo 0 step 2) {
                i = (i + 1) % 256
                j = (j + sBox[i]) % 256
                swap(sBox, i, j)
                val xorVal = sBox[(sBox[i] + sBox[j]) % 256]
                if (k < state.length) state.payload[k] = state.payload[k] xor xorVal
            }
        }

        private fun mod12ShuffleB(fnKey: IntArray, state: State) {
            val sIters = (state.length - 1) and -2
            val sBox = vQmi(state.key3, fnKey, state.key1)
            var i = 0; var j = 0
            for (k in sIters downTo 0 step 2) {
                i = (i + 1) % 256
                j = (j + sBox[i]) % 256
                swap(sBox, i, j)
                val xorVal = sBox[(sBox[i] + sBox[j]) % 256]
                if (k < state.length) state.payload[k] = state.payload[k] xor xorVal
            }
        }

        private fun mod15Mix(state: State) {
            val limit = minOf(32, state.length)
            for (s in 0 until limit) {
                val c = state.payload[s] xor state.key1[s] xor state.key2[s] xor state.key3[s]

                fun getTarget(bits: Int): IntArray = when(bits) {
                    0 -> state.key1; 4 -> state.key2; 8 -> state.key3; else -> state.payload
                }

                val tArr = getTarget(c and 12)
                var tVal = tArr[s]
                when(c and 3) {
                    0 -> { val tmp = state.key1[s]; state.key1[s] = tVal; tVal = tmp }
                    1 -> { val tmp = state.key2[s]; state.key2[s] = tVal; tVal = tmp }
                    2 -> { val tmp = state.key3[s]; state.key3[s] = tVal; tVal = tmp }
                    3 -> { val tmp = state.payload[s]; state.payload[s] = tVal; tVal = tmp }
                }
                getTarget(c and 12)[s] = tVal

                val bits2 = c and 192
                val tArr2 = when(bits2) { 0 -> state.key1; 64 -> state.key2; 128 -> state.key3; else -> state.payload }

                var tVal2 = tArr2[s]
                when(c and 48) {
                    0 -> { val tmp = state.key1[s]; state.key1[s] = tVal2; tVal2 = tmp }
                    16 -> { val tmp = state.key2[s]; state.key2[s] = tVal2; tVal2 = tmp }
                    32 -> { val tmp = state.key3[s]; state.key3[s] = tVal2; tVal2 = tmp }
                    else -> { val tmp = state.payload[s]; state.payload[s] = tVal2; tVal2 = tmp }
                }

                when (bits2) {
                    0 -> state.key1[s] = tVal2
                    64 -> state.key2[s] = tVal2
                    128 -> state.key3[s] = tVal2
                    else -> state.payload[s] = tVal2
                }
            }
        }

        private fun mod7UpdateKeys(fnKey: IntArray, state: State) {
            state.key3 = vSmi(state.key3, state.key2, state.key1, fnKey)
            state.key2 = vSmi(state.key2, state.key1, fnKey, state.key3)
            state.key1 = vSmi(state.key1, fnKey, state.key3, state.key2)
        }

        private fun mod4DecryptLoop(fnKey: IntArray, state: State) {
            val sBox = vQmi(state.key3, state.key2, fnKey)
            var i = 0; var j = 0
            for (k in 0 until state.length) {
                i = (i + 1) % 256
                j = (j + sBox[i]) % 256
                swap(sBox, i, j)
                val xorVal = sBox[(sBox[i] + sBox[j]) % 256]
                state.payload[k] = state.payload[k] xor xorVal
            }
        }

        private fun mod13Utf8Decode(bytesArr: IntArray, length: Int): String {
            val bytes = ByteArray(length) { bytesArr[it].toByte() }
            return String(bytes, StandardCharsets.UTF_8)
        }
    }

    object PublusImage {
        fun generateFilename(pageId: String, keys: List<IntArray>): String {
            val k1 = keys[0]; val k2 = keys[1]; val k3 = keys[2]
            val fileName = "0"
            val parentFolder = "$pageId/"

            val b9W = IntArray(k1.size)
            for (i in k1.indices) b9W[i] = k1[i] xor k2[i] xor k3[i]

            val jdf = "10"

            val pathStr = parentFolder + fileName
            val pathChars = pathStr.toCharArray()
            val pathLength = pathStr.length

            val vBef = (1 + pathLength) shl 1
            val vCef = IntArray(vBef)
            vCef[0] = 0
            vCef[1] = 59
            var vPdf = 2
            for (vOdf in 0 until pathLength) {
                val vSdf = pathChars[vOdf].code
                vCef[vPdf++] = (vSdf ushr 8)
                vCef[vPdf++] = vSdf and 255
            }

            var vFef = 3
            var vEef = (fileName.length shl 1) + vBef + vBef
            while(vEef < 256) {
                vFef++
                vEef += vBef
            }

            var vJef = 1670739
            var vKef = 1282576
            var vLef = 2237221

            var i = (1 + parentFolder.length) shl 1
            var j = 0
            (0 until vFef).forEach { _ ->
                while (i < vBef) {
                    vLef = vLef xor (vCef[i++] xor b9W[j++])
                    val vIef = 435 * vLef
                    val vHef = 435 * vKef + ((vLef and 7) shl 18) + (vIef ushr 22)
                    val vGef = 435 * vJef + ((vKef and 3) shl 19) + ((vLef and 4194296) ushr 3) + (vHef ushr 21)
                    vLef = vIef and 4194303
                    vKef = vHef and 2097151
                    vJef = vGef and 2097151
                    if (j >= b9W.size) j = 0
                }
                i = 0
            }

            val vMef = IntArray(16)
            fun pval(arr: IntArray, idx: Int, v: Int) {
                fun vval(v: Int) = if (v < 10) 48 + v else 87 + v
                arr[idx] = vval(v ushr 4)
                arr[idx+1] = vval(v and 15)
            }

            for (idx in 0 until 16 step 2) {
                val bIdx = idx / 2
                val keyByte = b9W[bIdx]
                val value = when(idx) {
                    0 -> (vJef ushr 13) xor keyByte
                    2 -> ((vJef ushr 5) and 255) xor keyByte
                    4 -> (((vJef and 31) shl 3) or (vKef ushr 18)) xor keyByte
                    6 -> ((vKef ushr 10) and 255) xor keyByte
                    8 -> ((vKef ushr 2) and 255) xor keyByte
                    10 -> (((vKef and 3) shl 6) or (vLef ushr 16)) xor keyByte
                    12 -> ((vLef ushr 8) and 255) xor keyByte
                    14 -> (vLef and 255) xor keyByte
                    else -> 0
                }
                pval(vMef, idx, value)
            }

            val hash = String(vMef.map { it.toChar() }.toCharArray())
            return "$parentFolder$jdf$hash.jpeg"
        }

        fun unscramble(bitmap: Bitmap, page: PublusPageAttributes, keys: List<IntArray>, pageId: String): Bitmap {
            val k1 = keys[0]; val k2 = keys[1]; val k3 = keys[2]

            var v0if = 47
            for (c in pageId) v0if += c.code
            val fileName = page.no.toString()
            for (c in fileName) v0if += c.code
            v0if += (k1.sum() + k2.sum() + k3.sum())

            var v9if = v0if and 255
            v9if = v9if or (v9if shl 8)
            v9if = v9if or (v9if shl 16)

            val b2yLen = B2y.V_CGH.size * B2y.V_DGH.size
            val b0a = v0if % b2yLen
            val b0j = (v9if xor vMhf(k1) xor page.ns.toInt())
            val b0k = (v9if xor vMhf(k2) xor page.ps.toInt())
            val b0n = (v9if xor vMhf(k3) xor page.rs.toInt())

            val blocksX = floor(bitmap.width.toDouble() / page.blockWidth).toInt()
            val blocksY = floor(bitmap.height.toDouble() / page.blockHeight).toInt()
            val lastBlockWidth = bitmap.width % page.blockWidth
            val lastBlockHeight = bitmap.height % page.blockHeight

            val v14j = (blocksX + 1) shl 1
            val v24j = (blocksY + 1) shl 1

            val b2y = B2y()
            val v64j = b0a xor blocksX xor blocksY
            val v74j = v64j % B2y.V_DGH.size
            val v84j = ((v64j - v74j) / B2y.V_DGH.size) % B2y.V_CGH.size

            b2y.b9es(v84j, v74j)
            b2y.b0o(b0j xor b0k xor b0n)

            val v94j = (b2y.b4k(65536).toLong() + b2y.b4k(65536).toLong() * 65536 + b2y.b4k(512).toLong() * 4294967296L)

            val vD4j = a3f(
                v94j.toDouble(),
                (blocksX.toLong() * 4294967296L + (b0j.toLong() and 0xFFFFFFFFL)).toDouble(),
                (blocksY.toLong() * 4294967296L + (b0k.toLong() and 0xFFFFFFFFL)).toDouble(),
                (b0a.toLong() * 4294967296L + (b0n.toLong() and 0xFFFFFFFFL)).toDouble()
            )

            val moves = mutableListOf<Move>()

            fun process(index: Int, total: Int, stepW: Int, stepH: Int) {
                var idx = index
                if (stepW != 0 && stepH != 0) {
                    while (idx < total) {
                        val vF4j = vD4j[idx++]
                        val vG4j = vD4j[idx++]
                        val vH4j = vF4j % v14j
                        val vI4j = vG4j % v24j
                        val vJ4j = (vG4j - vI4j) / v24j
                        val vK4j = (vF4j - vH4j) / v14j

                        moves.add(Move(
                            srcX = vH4j * page.blockWidth - (if(vH4j > blocksX) (blocksX + 1) * page.blockWidth - lastBlockWidth else 0),
                            srcY = vI4j * page.blockHeight - (if(vI4j > blocksY) (blocksY + 1) * page.blockHeight - lastBlockHeight else 0),
                            destX = vJ4j * page.blockWidth - (if(vJ4j > blocksX) (blocksX + 1) * page.blockWidth - lastBlockWidth else 0),
                            destY = vK4j * page.blockHeight - (if(vK4j > blocksY) (blocksY + 1) * page.blockHeight - lastBlockHeight else 0),
                            width = stepW,
                            height = stepH
                        ))
                    }
                }
            }

            var vx4j = 0
            var vy4j = blocksX * blocksY * 2

            process(vx4j, vy4j, page.blockWidth, page.blockHeight)
            vx4j = vy4j

            vy4j += 2
            process(vx4j, vy4j, lastBlockWidth, lastBlockHeight)
            vx4j = vy4j

            vy4j += blocksX * 2
            process(vx4j, vy4j, page.blockWidth, lastBlockHeight)
            vx4j = vy4j

            vy4j += blocksY * 2
            process(vx4j, vy4j, lastBlockWidth, page.blockHeight)

            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val srcRect = Rect()
            val dstRect = Rect()

            moves.forEach { m ->
                srcRect.set(m.destX, m.destY, m.destX + m.width, m.destY + m.height)
                dstRect.set(m.srcX, m.srcY, m.srcX + m.width, m.srcY + m.height)
                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            }

            if (page.contentWidth > 0 && page.contentHeight > 0 &&
                (result.width != page.contentWidth || result.height != page.contentHeight)
            ) {
                return Bitmap.createBitmap(result, 0, 0, page.contentWidth, page.contentHeight)
            }

            return result
        }

        class Move(val srcX: Int, val srcY: Int, val destX: Int, val destY: Int, val width: Int, val height: Int)

        private fun vMhf(arr: IntArray): Int {
            var vNhf = 0
            var vOhf = arr.size and -4
            if (vOhf > 32) vOhf = 32
            var i = 0
            while (i < vOhf) {
                vNhf = vNhf xor (arr[i++] shl 24)
                vNhf = vNhf xor (arr[i++] shl 16)
                vNhf = vNhf xor (arr[i++] shl 8)
                vNhf = vNhf xor (arr[i++] shl 0)
            }
            return vNhf
        }

        // A3f Logic
        private fun a3f(p1: Double, p2: Double, p3: Double, p4: Double): IntArray {
            val tog = B2y()
            val vUog = (p2.toLong().toInt() xor p3.toLong().toInt() xor p4.toLong().toInt())
            val vVog = floor(p1 / 65536).toInt()
            val vWog = floor(p2 / 65536).toInt()
            val vXog = floor(p3 / 65536).toInt()
            val vYog = floor(p4 / 65536).toInt()

            var v1pg = vWog xor vXog xor vYog
            var v2pg = vVog xor vYog
            var v3pg = p1.toLong().toInt() xor p2.toLong().toInt()
            var v4pg = p1.toLong().toInt() xor p3.toLong().toInt()
            var v5pg = p1.toLong().toInt() xor p4.toLong().toInt()

            v1pg = v1pg ushr 16
            val v6pg = v1pg % B2y.V_DGH.size
            val v7pg = ((v1pg - v6pg) / B2y.V_DGH.size) % B2y.V_CGH.size

            tog.b9es(v7pg, v6pg)
            tog.b0o(vUog)

            val v9pg = tog.b4k(65536) or (tog.b4k(65536) shl 16)
            val vApg = tog.b4k(512)
            val vBpg = vWog ushr 16
            val vCpg = vXog ushr 16

            v2pg = (v2pg ushr 16) xor vApg
            v3pg = v3pg xor v9pg
            v4pg = v4pg xor v9pg
            v5pg = v5pg xor v9pg

            val vDpg = v2pg % B2y.V_DGH.size
            val vEpg = ((v2pg - vDpg) / B2y.V_DGH.size) % B2y.V_CGH.size

            tog.b9es(vEpg, vDpg)
            tog.b0o(v3pg)
            val vFpg = vMqg(tog, vBpg * vCpg)
            tog.b0o(v4pg)

            val vGpg = v6qg(tog, vBpg)
            val vHpg = v6qg(tog, vCpg)
            val vIpg = v7qg(tog, vGpg, vBpg)
            val vJpg = v7qg(tog, vHpg, vCpg)
            tog.b0o(v5pg)

            val maxLen = maxOf(vBpg, vCpg, vBpg*vCpg) * 2 + 500
            val kArr = IntArray(maxLen); val lArr = IntArray(maxLen)

            v9qg(tog, kArr, lArr, vGpg, vHpg, vBpg, vCpg)

            val vMpg = vMqg(tog, vBpg)
            val vNpg = vMqg(tog, vCpg)

            val oArr = IntArray(maxLen); val pArr = IntArray(maxLen)
            v9qg(tog, pArr, oArr, vIpg, vJpg, vBpg, vCpg)

            return vQpg(vBpg, vCpg, vFpg, vMpg, vNpg, oArr, pArr, vIpg, vJpg, lArr, kArr, vGpg, vHpg)
        }

        private fun vMqg(tog: B2y, total: Int): IntArray {
            val arr = IntArray(total)
            for (i in 0 until total) {
                val n = tog.b4k(i + 1)
                arr[i] = arr[n]
                arr[n] = i
            }
            return arr
        }

        private fun v6qg(tog: B2y, v: Int) = if (v < 4) tog.b4k(v + 1) else tog.b4k(v - 1) + 1
        private fun v7qg(tog: B2y, ye: Int, ee: Int): Int {
            if (ee <= 0) return 0
            val v = tog.b4k(ee)
            return if (v < ye) v else v + 1
        }

        private fun v9qg(tog: B2y, p2: IntArray, p3: IntArray, p4: Int, p5: Int, p6: Int, p7: Int) {
            var vAqg: Int; var vBqg: Int; var vCqg: Int
            var vDqg = p6; var vEqg = p7
            var vFqg = p4; var vGqg = p5
            var vHqg = 0; var vIqg = 0
            val vJqg = -1

            while (vDqg + vEqg > 0) {
                vAqg = tog.b4k(vDqg + vEqg)
                if (vAqg < vDqg) {
                    if (vAqg < vFqg) {
                        vBqg = vIqg
                        while (vBqg > 0 && vHqg < p2[vBqg + vJqg]) vBqg--
                        vCqg = vIqg + vEqg
                        while (vCqg < p7 && vHqg < p2[vCqg]) vCqg++
                        p3[vHqg] = tog.b4k(vCqg - vBqg) + vBqg
                        vHqg++
                        vFqg--
                    } else {
                        vBqg = vIqg
                        while (vBqg > 0 && vHqg + vDqg > p2[vBqg + vJqg]) vBqg--
                        vCqg = vIqg + vEqg
                        while (vCqg < p7 && vHqg + vDqg > p2[vCqg]) vCqg++
                        p3[vHqg + vDqg + vJqg] = tog.b4k(vCqg - vBqg) + vBqg
                    }
                    vDqg--
                } else {
                    if (vAqg - vDqg < vGqg) {
                        vBqg = vHqg
                        while (vBqg > 0 && vIqg < p3[vBqg + vJqg]) vBqg--
                        vCqg = vHqg + vDqg
                        while (vCqg < p6 && vIqg < p3[vCqg]) vCqg++
                        p2[vIqg] = tog.b4k(vCqg - vBqg) + vBqg
                        vIqg++
                        vGqg--
                    } else {
                        vBqg = vHqg
                        while (vBqg > 0 && vIqg + vEqg > p3[vBqg + vJqg]) vBqg--
                        vCqg = vHqg + vDqg
                        while (vCqg < p6 && vIqg + vEqg > p3[vCqg]) vCqg++
                        p2[vIqg + vEqg + vJqg] = tog.b4k(vCqg - vBqg) + vBqg
                    }
                    vEqg--
                }
            }
        }

        private fun vQpg(p1: Int, p2: Int, p3: IntArray, p4: IntArray, p5: IntArray, p6: IntArray, p7: IntArray, p8: Int, p9: Int, p10: IntArray, p11: IntArray, p12: Int, p13: Int): IntArray {
            val result = mutableListOf<Int>()
            val v1qg = p1 + 1; val v2qg = p2 + 1
            val v3qg = v1qg shl 1; val v4qg = v2qg shl 1

            for (vVpg in 0 until p1) {
                for (vWpg in 0 until p2) {
                    val vZpg = p3[vVpg + vWpg * p1]
                    val vXpg = vZpg % p1
                    val vYpg = (vZpg - vXpg) / p1
                    val vRpg = if (vVpg < p11[vWpg]) vVpg else vVpg + v1qg
                    val vSpg = if (vWpg < p10[vVpg]) vWpg else vWpg + v2qg
                    val vTpg = if (vXpg < p7[vYpg]) vXpg else vXpg + v1qg
                    val vUpg = if (vYpg < p6[vXpg]) vYpg else vYpg + v2qg
                    result.add(vUpg * v3qg + vRpg)
                    result.add(vTpg * v4qg + vSpg)
                }
            }
            result.add(p9 * v3qg + p12)
            result.add(p8 * v4qg + p13)

            for (vVpg in 0 until p1) {
                val vXpg = p4[vVpg]
                val vRpg = if (vVpg < p12) vVpg else vVpg + v1qg
                val vTpg = if (vXpg < p8) vXpg else vXpg + v1qg
                result.add(p6[vXpg] * v3qg + vRpg)
                result.add(vTpg * v4qg + p10[vVpg])
            }

            for (vWpg in 0 until p2) {
                val vYpg = p5[vWpg]
                val vSpg = if (vWpg < p13) vWpg else vWpg + v2qg
                val vUpg = if (vYpg < p9) vYpg else vYpg + v2qg
                result.add(vUpg * v3qg + p11[vWpg])
                result.add(p7[vYpg] * v4qg + vSpg)
            }

            return result.toIntArray()
        }
    }

    class B2y {
        companion object {
            val V_CGH = arrayOf(
                intArrayOf(1,3,10), intArrayOf(1,5,16), intArrayOf(1,5,19), intArrayOf(1,9,29), intArrayOf(1,11,6), intArrayOf(1,11,16), intArrayOf(1,19,3), intArrayOf(1,21,20), intArrayOf(1,27,27),
                intArrayOf(2,5,15), intArrayOf(2,5,21), intArrayOf(2,7,7), intArrayOf(2,7,9), intArrayOf(2,7,25), intArrayOf(2,9,15), intArrayOf(2,15,17), intArrayOf(2,15,25), intArrayOf(2,21,9),
                intArrayOf(3,1,14), intArrayOf(3,3,26), intArrayOf(3,3,28), intArrayOf(3,3,29), intArrayOf(3,5,20), intArrayOf(3,5,22), intArrayOf(3,5,25), intArrayOf(3,7,29), intArrayOf(3,13,7), intArrayOf(3,23,25), intArrayOf(3,25,24), intArrayOf(3,27,11),
                intArrayOf(4,3,17), intArrayOf(4,3,27), intArrayOf(4,5,15),
                intArrayOf(5,3,21), intArrayOf(5,7,22), intArrayOf(5,9,7), intArrayOf(5,9,28), intArrayOf(5,9,31), intArrayOf(5,13,6), intArrayOf(5,15,17), intArrayOf(5,17,13), intArrayOf(5,21,12), intArrayOf(5,27,8), intArrayOf(5,27,21), intArrayOf(5,27,25), intArrayOf(5,27,28),
                intArrayOf(6,1,11), intArrayOf(6,3,17), intArrayOf(6,17,9), intArrayOf(6,21,7), intArrayOf(6,21,13),
                intArrayOf(7,1,9), intArrayOf(7,1,18), intArrayOf(7,1,25), intArrayOf(7,13,25), intArrayOf(7,17,21), intArrayOf(7,25,12), intArrayOf(7,25,20),
                intArrayOf(8,7,23), intArrayOf(8,9,23),
                intArrayOf(9,5,14), intArrayOf(9,5,25), intArrayOf(9,11,19), intArrayOf(9,21,16),
                intArrayOf(10,9,21), intArrayOf(10,9,25),
                intArrayOf(11,7,12), intArrayOf(11,7,16), intArrayOf(11,17,13), intArrayOf(11,21,13),
                intArrayOf(12,9,23),
                intArrayOf(13,3,17), intArrayOf(13,3,27), intArrayOf(13,5,19), intArrayOf(13,17,15),
                intArrayOf(14,1,15), intArrayOf(14,13,15),
                intArrayOf(15,1,29),
                intArrayOf(17,15,20), intArrayOf(17,15,23), intArrayOf(17,15,26)
            )
            val V_DGH = listOf(
                { p1: Int, p2: Int, p3: Int, p4: Int -> var r = p1; r = r xor (r shl p2); r = r xor (r ushr p3); r = r xor (r shl p4); r },
                { p1: Int, p2: Int, p3: Int, p4: Int -> var r = p1; r = r xor (r shl p4); r = r xor (r ushr p3); r = r xor (r shl p2); r },
                { p1: Int, p2: Int, p3: Int, p4: Int -> var r = p1; r = r xor (r ushr p2); r = r xor (r shl p3); r = r xor (r ushr p4); r },
                { p1: Int, p2: Int, p3: Int, p4: Int -> var r = p1; r = r xor (r ushr p4); r = r xor (r shl p3); r = r xor (r ushr p2); r },
                { p1: Int, p2: Int, p3: Int, p4: Int -> var r = p1; r = r xor (r shl p2); r = r xor (r shl p4); r = r xor (r ushr p3); r },
                { p1: Int, p2: Int, p3: Int, p4: Int -> var r = p1; r = r xor (r ushr p2); r = r xor (r ushr p4); r = r xor (r shl p3); r }
            )
            const val V_GGH = 2463534242.toInt()
        }

        private var vJgh = V_GGH
        private var vLgh = V_CGH[74][0]
        private var vMgh = V_CGH[74][1]
        private var vNgh = V_CGH[74][2]
        private var vOgh = V_DGH[0]

        fun b9es(EEyY_: Int, LEyY_: Int) {
            vJgh = V_GGH
            val vPgh = V_CGH[EEyY_]
            vLgh = vPgh[0]
            vMgh = vPgh[1]
            vNgh = vPgh[2]
            vOgh = V_DGH[LEyY_]
        }

        fun b0o(p1: Int) {
            vJgh = if (p1 != 0) p1 else V_GGH
        }

        fun b4k(p1: Int): Int {
            if (p1 <= 1) return 0
            val vVgh = (-1 - p1)
            var vSgh: Int
            var vTgh: Int
            var vUgh = vJgh
            do {
                vUgh = vOgh(vUgh, vLgh, vMgh, vNgh)
                vTgh = vUgh - 1
                vSgh = Integer.remainderUnsigned(vTgh, p1)
            } while (Integer.compareUnsigned(vVgh, vTgh - vSgh) < 0)

            vJgh = vUgh
            return vSgh
        }
    }
}
