package eu.kanade.tachiyomi.lib.textinterceptor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class TextInterceptor : Interceptor {
    // With help from:
    // https://github.com/tachiyomiorg/tachiyomi-extensions/pull/13304#issuecomment-1234532897
    // https://medium.com/over-engineering/drawing-multiline-text-to-canvas-on-android-9b98f0bfa16a

    companion object {
        // Designer values:
        private const val WIDTH: Int = 1000
        private const val X_PADDING: Float = 50f
        private const val Y_PADDING: Float = 25f
        private const val HEADING_FONT_SIZE: Float = 36f
        private const val BODY_FONT_SIZE: Float = 30f
        private const val SPACING_MULT: Float = 1.1f
        private const val SPACING_ADD: Float = 2f

        // No need to touch this one:
        private const val HOST = TextInterceptorHelper.HOST
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)

        val creator = textFixer("Author's Notes from ${url.pathSegments[0]}")
        val story = textFixer(url.pathSegments[1])

        // Heading
        val paintHeading = TextPaint().apply {
            color = Color.BLACK
            textSize = HEADING_FONT_SIZE
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        @Suppress("DEPRECATION")
        val heading = StaticLayout(
            creator, paintHeading, (WIDTH - 2 * X_PADDING).toInt(),
            Layout.Alignment.ALIGN_NORMAL, SPACING_MULT, SPACING_ADD, true
        )

        // Body
        val paintBody = TextPaint().apply {
            color = Color.BLACK
            textSize = BODY_FONT_SIZE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        @Suppress("DEPRECATION")
        val body = StaticLayout(
            story, paintBody, (WIDTH - 2 * X_PADDING).toInt(),
            Layout.Alignment.ALIGN_NORMAL, SPACING_MULT, SPACING_ADD, true
        )

        // Image building
        val imgHeight: Int = (heading.height + body.height + 2 * Y_PADDING).toInt()
        val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, imgHeight, Bitmap.Config.ARGB_8888)

        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            heading.draw(this, X_PADDING, Y_PADDING)
            body.draw(this, X_PADDING, Y_PADDING + heading.height.toFloat())
        }

        // Image converting & returning
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)
        val responseBody = stream.toByteArray().toResponseBody("image/png".toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun textFixer(htmlString: String): String {
        return if (Build.VERSION.SDK_INT >= 24) {
            Html.fromHtml(htmlString , Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(htmlString).toString()
        }
    }

    @Suppress("SameParameterValue")
    private fun StaticLayout.draw(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        this.draw(canvas)
        canvas.restore()
    }
}

object TextInterceptorHelper {

    const val HOST = "tachiyomi-lib-textinterceptor"

    fun createUrl(creator: String, text: String): String {
        return "http://$HOST/" + Uri.encode(creator) + "/" + Uri.encode(text)
    }
}
