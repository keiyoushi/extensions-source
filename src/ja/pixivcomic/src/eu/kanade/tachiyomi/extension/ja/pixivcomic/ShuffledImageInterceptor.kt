package eu.kanade.tachiyomi.extension.ja.pixivcomic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.floor

private const val SHUFFLE_SALT = "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe"
private const val BYTES_PER_PIXEL = 4
private const val GRID_SIZE = 32

internal class ShuffledImageInterceptor(private val key: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.headers["X-Cobalt-Thumber-Parameter-GridShuffle-Key"] == null) {
            return response
        }

        val imageBody = response.body.byteStream()
            .toDeShuffledImage(key)

        return response.newBuilder()
            .body(imageBody)
            .build()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun InputStream.toDeShuffledImage(key: String): ResponseBody {
        // get the image color data
        val shuffledImageBitmap = BitmapFactory.decodeStream(this)

        val width = shuffledImageBitmap.width
        val height = shuffledImageBitmap.height

        val shuffledImageArray = UByteArray(width * height * BYTES_PER_PIXEL)

        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = shuffledImageBitmap.getPixel(x, y)

                val alpha = pixel shr 24 and 0xff
                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff

                shuffledImageArray[index++] = alpha.toUByte()
                shuffledImageArray[index++] = red.toUByte()
                shuffledImageArray[index++] = green.toUByte()
                shuffledImageArray[index++] = blue.toUByte()
            }
        }

        // deShuffle the shuffled image
        val deShuffledImageArray = deShuffleImage(shuffledImageArray, width, height, key)

        // place it back together
        val deShuffledImageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(deShuffledImageBitmap)

        index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                // it's rgba
                val red = deShuffledImageArray[index++]
                val green = deShuffledImageArray[index++]
                val blue = deShuffledImageArray[index++]
                val alpha = deShuffledImageArray[index++]

                canvas.drawPoint(
                    x.toFloat(),
                    y.toFloat(),
                    Paint().apply {
                        setARGB(red.toInt(), green.toInt(), blue.toInt(), alpha.toInt())
                    },
                )
            }
        }

        return Buffer().run {
            deShuffledImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream())
            asResponseBody("image/png".toMediaType())
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun deShuffleImage(
        shuffledImageArray: UByteArray,
        width: Int,
        height: Int,
        key: String,
    ): UByteArray {
        val verticalGridTotal = ceil(height.toFloat() / GRID_SIZE).toInt()
        val horizontalGridTotal = floor(width.toFloat() / GRID_SIZE).toInt()
        val grid2DArray = Array(verticalGridTotal) { Array(horizontalGridTotal) { it } }

        val saltedKeyArray = SHUFFLE_SALT.plus(key).toByteArray()
        val saltedKeyHash = MessageDigest.getInstance("SHA-256").digest(saltedKeyArray).toUByteArray()
        val saltedKeyHashArray = saltedKeyHash.first16ByteBecome4UInt()
        val hash = HashAlgorithm(saltedKeyHashArray)

        for (i in 0 until 100) hash.next()

        for (i in 0 until verticalGridTotal) {
            val gridArray = grid2DArray[i]

            for (j in (horizontalGridTotal - 1) downTo 1) {
                val hashIndex = hash.next() % (j + 1).toUInt()
                val grid = gridArray[j]
                gridArray[j] = gridArray[hashIndex.toInt()]
                gridArray[hashIndex.toInt()] = grid
            }
        }

        for (i in 0 until verticalGridTotal) {
            val gridArray = grid2DArray[i]
            val indexOfIndexGridArray = gridArray.mapIndexed { index, _ ->
                gridArray.indexOf(index)
            }
            grid2DArray[i] = indexOfIndexGridArray.toTypedArray()
        }

        val deShuffledImageArray = UByteArray(shuffledImageArray.size)
        for (row in 0 until height) {
            val verticalGridIndex = floor(row.toFloat() / GRID_SIZE).toInt()
            val gridArray = grid2DArray[verticalGridIndex]

            for (horizontalGridIndex in 0 until horizontalGridTotal) {
                // places square grid to the places it supposed to be
                val gridFrom = gridArray[horizontalGridIndex]

                val gridToIndex = horizontalGridIndex * GRID_SIZE
                val toIndex = (row * width + gridToIndex) * BYTES_PER_PIXEL
                val gridFromIndex = gridFrom * GRID_SIZE
                val fromIndex = (row * width + gridFromIndex) * BYTES_PER_PIXEL

                val gridSizeBytes = GRID_SIZE * BYTES_PER_PIXEL
                for (i in 0 until gridSizeBytes) {
                    deShuffledImageArray[toIndex + i] = shuffledImageArray[fromIndex + i]
                }

                // copy the small part of image that don't get shuffled (most right side of the image)
                val horizontalIndex = horizontalGridTotal * GRID_SIZE
                val startIndex = (row * width + horizontalIndex) * BYTES_PER_PIXEL
                val lastIndex = (row * width + width) * BYTES_PER_PIXEL

                for (i in startIndex until lastIndex) {
                    deShuffledImageArray[i] = shuffledImageArray[i]
                }
            }
        }
        return deShuffledImageArray
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun UByteArray.first16ByteBecome4UInt(): UIntArray {
        val binaries = this.copyOfRange(0, 16).map {
            var binaryString = Integer.toBinaryString(it.toInt())
            for (i in binaryString.length until 8) {
                binaryString = "0$binaryString"
            }
            return@map binaryString
        }

        return UIntArray(4) { i ->
            val binariesIndexStart = i * 4
            val stringBuilder = StringBuilder()
            for (index in binariesIndexStart + 3 downTo binariesIndexStart) {
                stringBuilder.append(binaries[index])
            }
            Integer.parseUnsignedInt(stringBuilder.toString(), 2).toUInt()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private class HashAlgorithm(val hashArray: UIntArray) {
        init {
            if (hashArray.all { it == 0u }) {
                hashArray[0] = 1u
            }
        }

        fun next(): UInt {
            val e = 9u * shiftOr((5u * hashArray[1]), 7)
            val t = hashArray[1] shl 9

            hashArray[2] = hashArray[2] xor hashArray[0]
            hashArray[3] = hashArray[3] xor hashArray[1]
            hashArray[1] = hashArray[1] xor hashArray[2]
            hashArray[0] = hashArray[0] xor hashArray[3]
            hashArray[2] = hashArray[2] xor t
            hashArray[3] = shiftOr(this.hashArray[3], 11)

            return e
        }

        private fun shiftOr(value: UInt, by: Int): UInt {
            return (((value shl (by % 32))) or (value.toInt() ushr (32 - by)).toUInt())
        }
    }
}
