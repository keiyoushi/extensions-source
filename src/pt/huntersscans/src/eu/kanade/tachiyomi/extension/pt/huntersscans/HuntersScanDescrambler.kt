package eu.kanade.tachiyomi.extension.pt.huntersscans

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import keiyoushi.utils.parseAs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

object HuntersScanDescrambler {

    fun decryptHuntersPayload(payloadB64: String, keyB64: String): List<String> {
        val payload = String(Base64.decode(payloadB64, Base64.DEFAULT), Charset.forName("ISO-8859-1"))
        val key = String(Base64.decode(keyB64, Base64.DEFAULT), Charset.forName("ISO-8859-1"))
        val sb = StringBuilder()

        payload.forEachIndexed { i, ch ->
            val keyIndex = (i + key.length - 1) % key.length
            val c = ch.code - key[keyIndex].code
            sb.append(c.toChar())
        }

        return sb.toString().parseAs<List<String>>()
    }

    fun unscrambleImage(inputStream: InputStream, keyJson: String): InputStream {
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val key = keyJson.parseAs<List<Int>>()

        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val blockWidth = width / 16
        val blockHeight = height / 16

        val srcRect = Rect()
        val dstRect = Rect()

        for (i in 0 until 256) {
            val targetIdx = key[i]

            val srcX = (i % 16) * blockWidth
            val srcY = (i / 16) * blockHeight

            val dstX = (targetIdx % 16) * blockWidth
            val dstY = (targetIdx / 16) * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)
        return ByteArrayInputStream(output.toByteArray())
    }
}
