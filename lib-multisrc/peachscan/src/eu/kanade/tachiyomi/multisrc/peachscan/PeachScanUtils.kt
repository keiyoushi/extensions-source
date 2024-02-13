package eu.kanade.tachiyomi.multisrc.peachscan

import android.graphics.Bitmap
import android.graphics.Rect
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.lang.reflect.Method

/**
 * TachiyomiJ2K is on a 2-year-old version of ImageDecoder at the time of writing,
 * with a different signature than the one being used as a compile-only dependency.
 *
 * Because of this, if [ImageDecoder.decode] is called as-is on TachiyomiJ2K, we
 * end up with a [NoSuchMethodException].
 *
 * This is a hack for determining which signature to call when decoding images.
 */
object PeachScanUtils {
    private var decodeMethod: Method

    private var isNewDecodeMethod = false

    init {
        val rectClass = Rect::class.java
        val booleanClass = Boolean::class.java
        val intClass = Int::class.java
        val byteArrayClass = ByteArray::class.java

        decodeMethod = try {
            isNewDecodeMethod = true

            // decode(region, rgb565, sampleSize, applyColorManagement, displayProfile)
            ImageDecoder::class.java.getMethod("decode", rectClass, booleanClass, intClass, booleanClass, byteArrayClass)
        } catch (e: NoSuchMethodException) {
            isNewDecodeMethod = false

            // decode(region, rgb565, sampleSize)
            ImageDecoder::class.java.getMethod("decode", rectClass, booleanClass, intClass)
        }
    }

    fun decodeImage(data: ByteArray, rgb565: Boolean, filename: String, entryName: String): Bitmap {
        val decoder = ImageDecoder.newInstance(ByteArrayInputStream(data))

        if (decoder == null || decoder.width <= 0 || decoder.height <= 0) {
            throw IOException("Falha ao inicializar o decodificador de imagem")
        }

        val rect = Rect(0, 0, decoder.width, decoder.height)
        val bitmap = if (isNewDecodeMethod) {
            decodeMethod.invoke(decoder, rect, rgb565, 1, false, null)
        } else {
            decodeMethod.invoke(decoder, rect, rgb565, 1)
        } as Bitmap?

        decoder.recycle()

        if (bitmap == null) {
            throw IOException("Não foi possível decodificar a imagem $filename#$entryName")
        }

        return bitmap
    }
}
