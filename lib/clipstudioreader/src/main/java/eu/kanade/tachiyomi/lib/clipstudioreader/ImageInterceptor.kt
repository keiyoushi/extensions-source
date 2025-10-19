package eu.kanade.tachiyomi.lib.clipstudioreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import kotlin.math.floor

class ImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        val scrambleArray = url.queryParameter("scrambleArray")
        val scrambleGridW = url.queryParameter("scrambleGridW")?.toIntOrNull()
        val scrambleGridH = url.queryParameter("scrambleGridH")?.toIntOrNull()

        if (scrambleArray.isNullOrEmpty() || scrambleGridW == null || scrambleGridH == null) {
            return chain.proceed(request)
        }

        val newUrl = url.newBuilder()
            .removeAllQueryParameters("scrambleArray")
            .removeAllQueryParameters("scrambleGridW")
            .removeAllQueryParameters("scrambleGridH")
            .build()
        val newRequest = request.newBuilder().url(newUrl).build()

        val response = chain.proceed(newRequest)
        if (!response.isSuccessful) {
            return response
        }

        val scrambleMapping = scrambleArray.split(',').map { it.toInt() }
        val scrambledImg = BitmapFactory.decodeStream(response.body.byteStream())
        val descrambledImg = unscrambleImage(scrambledImg, scrambleMapping, scrambleGridW, scrambleGridH)

        val output = ByteArrayOutputStream()
        descrambledImg.compress(Bitmap.CompressFormat.JPEG, 90, output)
        val body = output.toByteArray().toResponseBody("image/jpeg".toMediaType())

        return response.newBuilder().body(body).build()
    }

    private fun unscrambleImage(
        image: Bitmap,
        scrambleMapping: List<Int>,
        gridWidth: Int,
        gridHeight: Int,
    ): Bitmap {
        val descrambledImg = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(descrambledImg)

        val pieceWidth = 8 * floor(floor(image.width.toFloat() / gridWidth) / 8).toInt()
        val pieceHeight = 8 * floor(floor(image.height.toFloat() / gridHeight) / 8).toInt()

        if (scrambleMapping.size < gridWidth * gridHeight || image.width < 8 * gridWidth || image.height < 8 * gridHeight) {
            return image
        }

        for (scrambleIndex in scrambleMapping.indices) {
            val destX = scrambleIndex % gridWidth * pieceWidth
            val destY = floor(scrambleIndex.toFloat() / gridWidth).toInt() * pieceHeight
            val destRect = Rect(destX, destY, destX + pieceWidth, destY + pieceHeight)

            val sourcePieceIndex = scrambleMapping[scrambleIndex]
            val sourceX = sourcePieceIndex % gridWidth * pieceWidth
            val sourceY = floor(sourcePieceIndex.toFloat() / gridWidth).toInt() * pieceHeight
            val sourceRect = Rect(sourceX, sourceY, sourceX + pieceWidth, sourceY + pieceHeight)

            canvas.drawBitmap(image, sourceRect, destRect, null)
        }
        return descrambledImg
    }
}
