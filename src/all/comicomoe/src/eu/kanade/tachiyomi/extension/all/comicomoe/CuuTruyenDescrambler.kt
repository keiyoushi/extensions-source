package eu.kanade.tachiyomi.extension.all.comicomoe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import keiyoushi.utils.xor
import java.io.IOException

// CuuTruyen pages arrive as full-width strips in shuffled order; drmData lists where each belongs
object CuuTruyenDescrambler {

    private val KEY = "3141592653589793".toByteArray()

    fun unscramble(source: Bitmap, drmData: String): Bitmap {
        val mapping = Base64.decode(drmData, Base64.DEFAULT).xor(KEY).decodeToString()
        if (!mapping.startsWith("#v4|")) throw IOException("Unsupported CuuTruyen page data")

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var sourceY = 0
        mapping.split('|').drop(1).forEach { strip ->
            val (destinationY, height) = strip.split('-', limit = 2).map(String::toInt)
            canvas.drawBitmap(
                source,
                Rect(0, sourceY, source.width, sourceY + height),
                Rect(0, destinationY, source.width, destinationY + height),
                null,
            )
            sourceY += height
        }
        return result
    }
}
