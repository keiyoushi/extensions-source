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
    @Volatile
    private var selectedDecoder: Decoder? = null

    private val decoders = listOf(
        Decoder("ca.mpreg.imagedecoder.ImageDecoder", ::decodeMihon),
        Decoder("tachiyomi.decoder.ImageDecoder", ::decodeTachiyomi),
        Decoder("android.graphics.BitmapFactory", ::decodeAndroid),
    )

    private class Decoder(val name: String, private val decodeImage: (ByteArray) -> Bitmap) {
        fun decode(data: ByteArray): Bitmap = decodeImage(data)

        fun decodeSafely(data: ByteArray): Result<Bitmap> = try {
            Result.success(decode(data))
        } catch (error: Exception) {
            Result.failure(error)
        } catch (error: LinkageError) {
            Result.failure(error)
        }

        fun failureMessage(error: Throwable): String {
            val cause = when (error) {
                is ExceptionInInitializerError -> error.cause ?: error
                else -> error
            }
            val description = cause.message
                .takeIf { cause.javaClass == IOException::class.java || cause.javaClass == Exception::class.java }
                ?.let { "${cause.javaClass.simpleName}: $it" }
                ?: cause.javaClass.simpleName
            return "$name ($description)"
        }
    }

    fun decode(data: ByteArray): Bitmap = if (selectedDecoder != null) {
        decodeFirstAvailable(data)
    } else {
        synchronized(this) { decodeFirstAvailable(data) }
    }

    private fun decodeFirstAvailable(data: ByteArray): Bitmap {
        val preferredDecoder = selectedDecoder
        val failures = mutableListOf<String>()
        var lastError: Throwable? = null
        val candidates = if (preferredDecoder == null) {
            decoders.asSequence()
        } else {
            sequenceOf(preferredDecoder) + decoders.asSequence().filterNot { it === preferredDecoder }
        }
        return candidates
            .firstNotNullOfOrNull { decoder ->
                decoder.decodeSafely(data)
                    .onSuccess { if (decoder !== preferredDecoder) selectedDecoder = decoder }
                    .onFailure { error ->
                        lastError = error
                        failures += decoder.failureMessage(error)
                    }
                    .getOrNull()
            } ?: run {
            val message = buildString {
                appendLine("No available decoder:")
                appendLine(failures.joinToString("\n") { "- $it" })
            }
            throw IOException(message, lastError)
        }
    }

    // Used by Mihon v0.21.0+ / r8055+
    private fun decodeMihon(data: ByteArray): Bitmap {
        val decoder = MihonImageDecoder.open(ByteArrayInputStream(data), true)
        return decoder.use { activeDecoder ->
            val frame = activeDecoder.decodeNext()
            frame.use { activeFrame ->
                val isHdr = activeDecoder.isHdr
                val config = if (isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
                val bytesPerPixel = if (isHdr) 8 else 4
                val frameWidth = activeFrame.width
                val frameHeight = activeFrame.height
                val pixels = activeFrame.image.rewind()
                val requiredSize = frameWidth.toLong() * frameHeight * bytesPerPixel
                if (pixels.remaining().toLong() < requiredSize) {
                    throw IOException("pixel buffer too small (${pixels.remaining()} < $requiredSize)")
                }
                Bitmap.createBitmap(frameWidth, frameHeight, config).apply {
                    copyPixelsFromBuffer(pixels)
                }
            }
        }
    }

    // Used by Tachiyomi v0.11.1+
    private fun decodeTachiyomi(data: ByteArray): Bitmap {
        val decoder = TachiyomiImageDecoder.Companion.newInstance(ByteArrayInputStream(data), false, null)
            ?: throw IOException("newInstance returned no decoder")
        return try {
            val rect = Rect(0, 0, decoder.width, decoder.height)
            decoder.decode(rect, 1) ?: throw IOException("decode returned no bitmap")
        } finally {
            decoder.recycle()
        }
    }

    // Used by Suwayomi. Android 12+ can also use it
    private fun decodeAndroid(data: ByteArray): Bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        ?: throw IOException("decode returned no bitmap")
}
