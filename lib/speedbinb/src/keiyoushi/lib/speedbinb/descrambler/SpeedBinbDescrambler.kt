package keiyoushi.lib.speedbinb.descrambler

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.lib.speedbinb.PtImgTranslation

abstract class SpeedBinbDescrambler {
    abstract fun isScrambled(): Boolean
    abstract fun getCanvasDimensions(width: Int, height: Int): Pair<Int, Int>
    abstract fun getDescrambleCoords(width: Int, height: Int): List<PtImgTranslation>

    fun descrambleImage(image: Bitmap): Bitmap {
        val (width, height) = getCanvasDimensions(image.width, image.height)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        getDescrambleCoords(image.width, image.height).forEach {
            val src = Rect(it.xsrc, it.ysrc, it.xsrc + it.width, it.ysrc + it.height)
            val dst = Rect(it.xdest, it.ydest, it.xdest + it.width, it.ydest + it.height)

            canvas.drawBitmap(image, src, dst, null)
        }

        return result
    }
}
