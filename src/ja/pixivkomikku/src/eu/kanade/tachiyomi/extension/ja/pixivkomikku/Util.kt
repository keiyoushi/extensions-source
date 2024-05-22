package eu.kanade.tachiyomi.extension.ja.pixivkomikku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

const val BYTES_PER_PIXEL = 4
const val GRID_SIZE = 32
const val SHUFFLE_SALT = "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe"
const val TIME_SALT = "mAtW1X8SzGS880fsjEXlM73QpS1i4kUMBhyhdaYySk8nWz533nrEunaSplg63fzT"

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ResponseBody.toBase64ImageString(key: String): String {
    val bytes = this.bytes()
    val shuffledImageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    Log.d("imageUByteArray", "${shuffledImageBitmap.width} ${shuffledImageBitmap.height}")

    val width = shuffledImageBitmap.width
    val height = shuffledImageBitmap.height

    val shuffledImageArray = UByteArray(shuffledImageBitmap.width * shuffledImageBitmap.height * BYTES_PER_PIXEL)
    var index1 = 0
    for (y in 0 until shuffledImageBitmap.height) {
        for (x in 0 until shuffledImageBitmap.width) {
            val pixel = shuffledImageBitmap.getPixel(x, y)

            val alpha = pixel shr 24 and 0xff
            val red = pixel shr 16 and 0xff
            val green = pixel shr 8 and 0xff
            val blue = pixel and 0xff

            shuffledImageArray[index1++] = alpha.toUByte()
            shuffledImageArray[index1++] = red.toUByte()
            shuffledImageArray[index1++] = green.toUByte()
            shuffledImageArray[index1++] = blue.toUByte()
        }
    }

    val deShuffledImageArray = deShuffleImage(shuffledImageArray, width, height, key)

    val deShuffledImageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(deShuffledImageBitmap)

    var index = 0
    for (y in 0 until deShuffledImageBitmap.height) {
        for (x in 0 until deShuffledImageBitmap.width) {
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
internal fun deShuffleImage(
    shuffledImageArray: UByteArray,
    width: Int,
    height: Int,
    key: String,
): UByteArray {
    Log.d("deshuffle", "deshuffle: 1")

    val verticalGridTotal = ceil(height.toFloat() / GRID_SIZE).toInt()
    val horizontalGridTotal = floor(width.toFloat() / GRID_SIZE).toInt()
    val grid2DArray = Array(verticalGridTotal) { Array(horizontalGridTotal) { it } }
    Log.d("deshuffle", "deshuffle: 2 $verticalGridTotal $horizontalGridTotal \n ${grid2DArray.map { it.map { it.toString() } }}")

    val saltedKeyArray = SHUFFLE_SALT.plus(key).toByteArray()
    Log.d("deshuffle", "2.1")
    val saltedKeyHash = MessageDigest.getInstance("SHA-256").digest(saltedKeyArray).toUByteArray()
    Log.d("deshuffle", "2.2")
    val saltedKeyHashArray = saltedKeyHash.first16ByteBecome4UInt()
    Log.d("deshuffle", "deshuffle: 3 ${saltedKeyArray.map { it.toString() }} \n\n ${saltedKeyHash.map { it.toString() }} \n\n ${saltedKeyHashArray.map { it.toString() }}")
    val shifter = SaltedKeyHashShifter(saltedKeyHashArray)

    Log.d("deshuffle", "deshuffle: 4")
    for (i in 0 until 100) shifter.next()

    var notStarted = true
    for (i in 0 until verticalGridTotal) {
        val horizontalGridArray = grid2DArray[i]
        Log.d("deshuffle", "deshuffle: 4.0.5 ${horizontalGridArray.map { it.toString() }}")

        for (j in (horizontalGridTotal - 1).downTo(1)) {
            val horizontalLocation = shifter.next() % (j + 1).toUInt()
            if (notStarted) Log.d("deshuffle", "deshuffle: 4.1 $horizontalLocation")
            val horizontalSquareValue = horizontalGridArray[j]
            if (notStarted) Log.d("deshuffle", "deshuffle: 4.2 $horizontalSquareValue")
            horizontalGridArray[j] = horizontalGridArray[horizontalLocation.toInt()]
            if (notStarted) Log.d("deshuffle", "deshuffle: 4.3 ${horizontalGridArray[j]}")
            horizontalGridArray[horizontalLocation.toInt()] = horizontalSquareValue
            if (notStarted) Log.d("deshuffle", "deshuffle: 4.4 ${horizontalGridArray[horizontalLocation.toInt()]}")

            notStarted = false
        }
    }

    Log.d("deshuffle", "deshuffle: 5")
    var notStarted2 = true
    for (i in 0 until verticalGridTotal) {
        val horizontalGridArray = grid2DArray[i]
        val valueBecomeIndex = horizontalGridArray.mapIndexed { index, _ ->
            horizontalGridArray.indexOf(index)
        }
        grid2DArray[i] = valueBecomeIndex.toTypedArray()
        if (notStarted2) Log.d("deshuffle", "deshuffle: 5.1 ${horizontalGridArray.map { it.toString() }} $valueBecomeIndex ${grid2DArray[i].map { it.toString() }}")
        notStarted2 = false
    }

    Log.d("deshuffle", "deshuffle: 6")
    var notStarted3 = true
    val deshuffledImageArray = UByteArray(shuffledImageArray.size)
    for (rowCount in 0 until height) {
        val verticalGridIndex = floor(rowCount.toFloat() / GRID_SIZE).toInt()
        val horizontalGridArray = grid2DArray[verticalGridIndex]
        if (notStarted3) Log.d("deshuffle", "deshuffle: 6.1 $verticalGridIndex ${horizontalGridArray.map { it.toString() }}")

        for (gridColumnCount in 0 until horizontalGridTotal) {
            val horizontalGridFrom = horizontalGridArray[gridColumnCount]

            val squareIndexTo = gridColumnCount * GRID_SIZE
            val toIndex = (rowCount * width + squareIndexTo) * BYTES_PER_PIXEL

            val horizontalGridIndex = horizontalGridFrom * GRID_SIZE
            val fromIndex = (rowCount * width + horizontalGridIndex) * BYTES_PER_PIXEL

            val gridIndexSize = GRID_SIZE * BYTES_PER_PIXEL
            if (notStarted3) Log.d("deshuffle", "deshuffle: 6.2 $horizontalGridFrom $squareIndexTo $toIndex $horizontalGridIndex $fromIndex $gridIndexSize")

            for (index in 0 until gridIndexSize) {
                deshuffledImageArray[toIndex + index] = shuffledImageArray[fromIndex + index]
            }

            val horizontalIndex = horizontalGridTotal * GRID_SIZE
            val startIndex = (rowCount * width + horizontalIndex) * BYTES_PER_PIXEL
            val lastIndex = (rowCount * width + width) * BYTES_PER_PIXEL
            if (notStarted3) Log.d("deshuffle", "deshuffle: 6.3 $horizontalIndex $startIndex $lastIndex")

            for (i in startIndex until lastIndex) {
                deshuffledImageArray[i] = shuffledImageArray[i]
            }
            notStarted3 = false
        }
    }
    return deshuffledImageArray
}

@OptIn(ExperimentalUnsignedTypes::class)
fun UByteArray.first16ByteBecome4UInt(): UIntArray {
    val binaries = this.copyOfRange(0, 16).map {
        var binaryString = Integer.toBinaryString(it.toInt())
        for (i in binaryString.length until 8) {
            binaryString = "0$binaryString"
        }
        return@map binaryString
    }
    Log.d("binaries", this.toList().toString())
    Log.d("binaries", this.copyOfRange(0, 15).toList().toString())
    Log.d("binaries", binaries.toString())

    return UIntArray(4) { i ->
        Log.d("UInt Array", i.toString())
        val binariesIndexStart = i * 4
        val stringBuilder = StringBuilder()
        for (index in binariesIndexStart + 3 downTo binariesIndexStart) {
            stringBuilder.append(binaries[index])
            Log.d("UInt for", index.toString())
        }
        Integer.parseUnsignedInt(stringBuilder.toString(), 2).toUInt()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
private class SaltedKeyHashShifter(val hashArray: UIntArray) {
    init {
        if (hashArray.all { it == 0u }) {
            hashArray[0] = 1u
        }
    }

    fun next(): UInt {
        val e = (9u * shift((5u * hashArray[1]), 7))
        val t = (hashArray[1] shl 9)

        hashArray[2] = (hashArray[2] xor hashArray[0])
        hashArray[3] = (hashArray[3] xor hashArray[1])
        hashArray[1] = (hashArray[1] xor hashArray[2])
        hashArray[0] = (hashArray[0] xor hashArray[3])
        hashArray[2] = (hashArray[2] xor t)
        hashArray[3] = shift(this.hashArray[3], 11)

        return e
    }

    private fun shift(e: UInt, t: Int): UInt {
        return (((e shl (t % 32))) or (e.toInt() ushr (32 - t)).toUInt())
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
@RequiresApi(Build.VERSION_CODES.O)
internal fun getTimeAndHash(): Pair<String, String> {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXXXX")
        .withZone(ZoneId.systemDefault())
    val timeFormatted = formatter.format(Instant.now())

    val saltedTimeArray = timeFormatted.plus(TIME_SALT).toByteArray()
    val saltedTimeHash = MessageDigest.getInstance("SHA-256").digest(saltedTimeArray).toUByteArray()
    val hexadecimalTimeHash = saltedTimeHash.joinToString("") {
        var hex = Integer.toHexString(it.toInt())
        if (hex.length < 2) {
            hex = "0$hex"
        }
        return@joinToString hex
    }

    Log.d("time", "$timeFormatted $hexadecimalTimeHash")

    return Pair(timeFormatted, hexadecimalTimeHash)
}
