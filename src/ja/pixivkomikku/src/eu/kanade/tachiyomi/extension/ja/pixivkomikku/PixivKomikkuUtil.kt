package eu.kanade.tachiyomi.extension.ja.pixivkomikku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Base64
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

internal fun randomString(): String {
    // the average length of key
    val length = (30..40).random()

    return buildString(length) {
        val charPool = ('a'..'z') + ('A'..'Z') + (0..9)

        for (i in 0 until length) {
            append(charPool.random())
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun getTimeAndHash(): Pair<String, String> {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
    val timeZoneOffset = getCurrentTimeZoneOffsetString()
    val timeFormatted = formatter.format(Date()).plus(timeZoneOffset)

    val saltedTimeArray = timeFormatted.plus(TIME_SALT).toByteArray()
    val saltedTimeHash = MessageDigest.getInstance("SHA-256").digest(saltedTimeArray).toUByteArray()
    val hexadecimalTimeHash = saltedTimeHash.joinToString("") {
        var hex = Integer.toHexString(it.toInt())
        if (hex.length < 2) {
            hex = "0$hex"
        }
        return@joinToString hex
    }

    return Pair(timeFormatted, hexadecimalTimeHash)
}

private fun getCurrentTimeZoneOffsetString(): String {
    val timeZone = TimeZone.getDefault()
    val offsetInMillis = timeZone.rawOffset

    val hours = offsetInMillis / (1000 * 60 * 60)
    val minutes = (offsetInMillis % (1000 * 60 * 60)) / (1000 * 60)

    val sign = if (hours >= 0) "+" else "-"
    val formattedHours = String.format("%02d", abs(hours))
    val formattedMinutes = String.format("%02d", abs(minutes))

    return "$sign$formattedHours:$formattedMinutes"
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ResponseBody.toBase64ImageString(key: String): String {
    // get the image color data
    val bytes = this.bytes()
    val shuffledImageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val width = shuffledImageBitmap.width
    val height = shuffledImageBitmap.height

    val shuffledImageArray = UByteArray(width * height * BYTES_PER_PIXEL)

    var index = 0
    for (y in 0 until shuffledImageBitmap.height) {
        for (x in 0 until shuffledImageBitmap.width) {
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
    for (y in 0 until deShuffledImageBitmap.height) {
        for (x in 0 until deShuffledImageBitmap.width) {
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

    val outStream = ByteArrayOutputStream()
    deShuffledImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)

    return Base64.encodeToString(outStream.toByteArray(), Base64.DEFAULT)
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

            val gridIndexSize = GRID_SIZE * BYTES_PER_PIXEL
            for (k in 0 until gridIndexSize) {
                deShuffledImageArray[toIndex + k] = shuffledImageArray[fromIndex + k]
            }

            // copy the small part of image that don't get shuffled (most right side of the image)
            val horizontalIndex = horizontalGridTotal * GRID_SIZE

            val startIndex = (row * width + horizontalIndex) * BYTES_PER_PIXEL
            val lastIndex = (row * width + width) * BYTES_PER_PIXEL

            for (k in startIndex until lastIndex) {
                deShuffledImageArray[k] = shuffledImageArray[k]
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

private const val TIME_SALT = "mAtW1X8SzGS880fsjEXlM73QpS1i4kUMBhyhdaYySk8nWz533nrEunaSplg63fzT"
private const val SHUFFLE_SALT = "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe"
private const val BYTES_PER_PIXEL = 4
private const val GRID_SIZE = 32
