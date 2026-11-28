package eu.kanade.tachiyomi.extension.en.mangago

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import app.cash.quickjs.QuickJs
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.decodeHex
import keiyoushi.utils.newAesCipher
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import java.io.InputStream
import javax.crypto.spec.IvParameterSpec

object Descrambler {
    fun decodeImageList(encryptedImages: ByteArray, chapterJs: String): List<String> {
        val key = findHexEncodedVariable(chapterJs, "key").decodeHex()
        val iv = findHexEncodedVariable(chapterJs, "iv").decodeHex()
        var imageList = encryptedImages.aesDecrypt(newAesCipher("AES/CBC/ZEROBYTEPADDING", key, IvParameterSpec(iv))).decodeToString()
        imageList = unscrambleImageList(imageList, chapterJs)
        val cols = COLS_REGEX.find(chapterJs)?.groupValues?.get(1).orEmpty()
        val imageKeyScript = chapterJs
            .substringAfter("var renImg = function(img,width,height,id){")
            .substringBefore("key = key.split(")
            .lineSequence()
            .filter { line -> JS_FILTERS.none(line::contains) }
            .joinToString("\n")
            .replace("img.src", "url")

        return QuickJs.create().use { quickJs ->
            quickJs.execute(REPLACE_POS_BYTECODE)
            quickJs.evaluate("function getDescramblingKey(url) { $imageKeyScript; return key; }")
            imageList.split(",").map { imageUrl ->
                if (imageUrl.contains("cspiclink")) {
                    val descKey = quickJs.evaluate("""getDescramblingKey("$imageUrl");""") as String
                    "$imageUrl#desckey=$descKey&cols=$cols"
                } else {
                    imageUrl
                }
            }
        }
    }

    fun unscrambleImage(image: InputStream, key: String, cols: Int): ResponseBody {
        val bitmap = BitmapFactory.decodeStream(image)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val unitWidth = bitmap.width / cols
        val unitHeight = bitmap.height / cols
        val keyArray = key.split("a")

        for (index in 0 until cols * cols) {
            val keyValue = keyArray[index].ifEmpty { "0" }.toInt()
            val destinationRow = keyValue.floorDiv(cols)
            val sourceRow = index.floorDiv(cols)
            val sourceX = (index - sourceRow * cols) * unitWidth
            val sourceY = sourceRow * unitHeight
            val destinationX = (keyValue - destinationRow * cols) * unitWidth
            val destinationY = destinationRow * unitHeight
            canvas.drawBitmap(
                bitmap,
                Rect(sourceX, sourceY, sourceX + unitWidth, sourceY + unitHeight),
                Rect(destinationX, destinationY, destinationX + unitWidth, destinationY + unitHeight),
                null,
            )
        }

        val buffer = okio.Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        bitmap.recycle()
        result.recycle()
        return buffer.asResponseBody("image/jpeg".toMediaTypeOrNull())
    }

    private fun findHexEncodedVariable(input: String, variable: String): String = HEX_VARIABLE_REGEX.findAll(input)
        .firstOrNull { it.groupValues[1] == variable }
        ?.groupValues
        ?.get(2)
        .orEmpty()

    private fun String.unscramble(keys: List<Int>): String {
        var result = this
        keys.reversed().forEach { key ->
            for (index in result.length - 1 downTo key) {
                if (index % 2 != 0) {
                    val previous = result[index - key]
                    result = result.replaceRange(index - key..index - key, result[index].toString())
                    result = result.replaceRange(index..index, previous.toString())
                }
            }
        }
        return result
    }

    private fun unscrambleImageList(imageList: String, js: String): String {
        val keyLocations = KEY_LOCATION_REGEX.findAll(js)
            .map { it.groupValues[1].toInt() }
            .distinct()
            .toList()
        // A non-digit at a key location: the list is already unscrambled
        val keys = keyLocations.map { imageList[it].digitToIntOrNull() ?: return imageList }
        var result = imageList
        keyLocations.forEachIndexed { index, location ->
            result = result.removeRange(location - index..location - index)
        }
        return result.unscramble(keys)
    }
}

private val KEY_LOCATION_REGEX = Regex("""str\.charAt\(\s*(\d+)\s*\)""")
private val COLS_REGEX = Regex("""var\s*widthnum\s*=\s*heightnum\s*=\s*(\d+);""")
private val HEX_VARIABLE_REGEX =
    Regex("""var\s+(key|iv)\s*=\s*CryptoJS\.enc\.Hex\.parse\("([0-9a-zA-Z]+)"\)""")
private val JS_FILTERS = listOf(
    "jQuery",
    "document",
    "getContext",
    "toDataURL",
    "getImageData",
    "width",
    "height",
)
private val REPLACE_POS_BYTECODE by lazy {
    QuickJs.create().use {
        it.compile(
            """
                    function replacePos(strObj, pos, replacetext) {
                        var str = strObj.substr(0, pos) + replacetext + strObj.substring(pos + 1, strObj.length);
                        return str;
                    }
            """.trimIndent(),
            "?",
        )
    }
}
