package eu.kanade.tachiyomi.multisrc.peachscan

import android.graphics.Bitmap
import android.graphics.Rect
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
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
    private var newInstanceMethod: Method

    private var classSignature = ClassSignature.Newest

    private enum class ClassSignature {
        Old, New, Newest
    }

    init {
        val rectClass = Rect::class.java
        val booleanClass = Boolean::class.java
        val intClass = Int::class.java
        val byteArrayClass = ByteArray::class.java
        val inputStreamClass = InputStream::class.java

        try {
            // Mihon Preview r6595+
            classSignature = ClassSignature.Newest

            // decode(region, sampleSize)
            decodeMethod = ImageDecoder::class.java.getMethod(
                "decode",
                rectClass,
                intClass,
            )

            // newInstance(stream, cropBorders, displayProfile)
            newInstanceMethod = ImageDecoder.Companion::class.java.getMethod(
                "newInstance",
                inputStreamClass,
                booleanClass,
                byteArrayClass,
            )
        } catch (_: NoSuchMethodException) {
            try {
                // Mihon Stable & forks
                classSignature = ClassSignature.New

                // decode(region, rgb565, sampleSize, applyColorManagement, displayProfile)
                decodeMethod = ImageDecoder::class.java.getMethod(
                    "decode",
                    rectClass,
                    booleanClass,
                    intClass,
                    booleanClass,
                    byteArrayClass,
                )

                // newInstance(stream, cropBorders)
                newInstanceMethod = ImageDecoder.Companion::class.java.getMethod(
                    "newInstance",
                    inputStreamClass,
                    booleanClass,
                )
            } catch (_: NoSuchMethodException) {
                // Tachiyomi J2k
                classSignature = ClassSignature.Old

                // decode(region, rgb565, sampleSize)
                decodeMethod =
                    ImageDecoder::class.java.getMethod(
                        "decode",
                        rectClass,
                        booleanClass,
                        intClass,
                    )

                // newInstance(stream, cropBorders)
                newInstanceMethod = ImageDecoder.Companion::class.java.getMethod(
                    "newInstance",
                    inputStreamClass,
                    booleanClass,
                )
            }
        }
    }

    fun decodeImage(data: ByteArray, rgb565: Boolean, filename: String, entryName: String): Bitmap {
        val decoder = when (classSignature) {
            ClassSignature.Newest -> newInstanceMethod.invoke(ImageDecoder.Companion, ByteArrayInputStream(data), false, null)
            else -> newInstanceMethod.invoke(ImageDecoder.Companion, ByteArrayInputStream(data), false)
        } as ImageDecoder?

        if (decoder == null || decoder.width <= 0 || decoder.height <= 0) {
            throw IOException("Falha ao inicializar o decodificador de imagem")
        }

        val rect = Rect(0, 0, decoder.width, decoder.height)
        val bitmap = when (classSignature) {
            ClassSignature.Newest -> decodeMethod.invoke(decoder, rect, 1)
            ClassSignature.New -> decodeMethod.invoke(decoder, rect, rgb565, 1, false, null)
            else -> decodeMethod.invoke(decoder, rect, rgb565, 1)
        } as Bitmap?

        decoder.recycle()

        if (bitmap == null) {
            throw IOException("Não foi possível decodificar a imagem $filename#$entryName")
        }

        return bitmap
    }
}
