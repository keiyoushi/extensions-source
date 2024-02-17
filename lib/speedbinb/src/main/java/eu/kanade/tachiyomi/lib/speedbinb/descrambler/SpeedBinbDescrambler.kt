package eu.kanade.tachiyomi.lib.speedbinb.descrambler

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.lib.speedbinb.PtImgTranslation
import java.io.ByteArrayOutputStream

abstract class SpeedBinbDescrambler {
    abstract fun isScrambled(): Boolean
    abstract fun canDescramble(): Boolean
    abstract fun getCanvasDimensions(): Pair<Int, Int>
    abstract fun getDescrambleCoords(): List<PtImgTranslation>

    open fun descrambleImage(image: Bitmap): ByteArray? {
        if (!isScrambled()) {
            return null
        }

        val (width, height) = getCanvasDimensions()
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        getDescrambleCoords().forEach {
            val src = Rect(it.xsrc, it.ysrc, it.xsrc + it.width, it.ysrc + it.height)
            val dst = Rect(it.xdest, it.ydest, it.xdest + it.width, it.ydest + it.height)

            canvas.drawBitmap(image, src, dst, null)
        }

        return ByteArrayOutputStream()
            .also {
                result.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            .toByteArray()
    }
}
