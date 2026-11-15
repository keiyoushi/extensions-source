package keiyoushi.lib.imagedecoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import java.io.ByteArrayInputStream
import java.io.IOException
import ca.mpreg.imagedecoder.ImageDecoder as MihonImageDecoder
import tachiyomi.decoder.ImageDecoder as TachiyomiImageDecoder

/** Decode images with the host app's decoder */
object ImageDecoder {
    // A decoder the host app doesn't ship fails to link; it's dropped for good then
    @Volatile
    private var decoders = listOf(::decodeMihon, ::decodeTachiyomi, ::decodeAndroid)

    fun decode(data: ByteArray): Bitmap {
        val failures = mutableListOf<Throwable>()
        for (decoder in decoders) {
            try {
                return decoder(data)
            } catch (e: LinkageError) {
                decoders -= decoder
                failures += e
            } catch (e: Exception) {
                failures += e
            }
        }
        throw IOException("No decoder could read the image").apply { failures.forEach(::addSuppressed) }
    }

    // Used by Mihon v0.21.0+ / r8055+
    private fun decodeMihon(data: ByteArray): Bitmap = MihonImageDecoder.open(ByteArrayInputStream(data)).use { decoder ->
        decoder.decodeNext().use { frame ->
            val config = if (decoder.isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
            Bitmap.createBitmap(frame.width, frame.height, config).apply {
                copyPixelsFromBuffer(frame.image)
            }
        }
    }

    // Used by Tachiyomi v0.11.1+
    private fun decodeTachiyomi(data: ByteArray): Bitmap {
        val decoder = TachiyomiImageDecoder.newInstance(ByteArrayInputStream(data))
            ?: throw IOException("newInstance returned no decoder")
        return try {
            decoder.decode(Rect(0, 0, decoder.width, decoder.height)) ?: throw IOException("decode returned no bitmap")
        } finally {
            decoder.recycle()
        }
    }

    // Used by Suwayomi. Android 12+ can also use it
    private fun decodeAndroid(data: ByteArray): Bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        ?: throw IOException("decode returned no bitmap")
}
