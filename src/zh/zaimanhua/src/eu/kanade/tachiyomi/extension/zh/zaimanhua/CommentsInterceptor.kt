package eu.kanade.tachiyomi.extension.zh.zaimanhua

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import eu.kanade.tachiyomi.extension.zh.zaimanhua.Zaimanhua.Companion.COMMENTS_FLAG
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

fun parseChapterComments(response: Response): List<String> {
    val result = response.parseAs<ResponseDto<CommentDataDto>>()
    val comments = result.data.toCommentList()
    return if (result.errmsg.isNotBlank()) {
        throw Exception(result.errmsg)
    } else {
        comments
    }
}

object CommentsInterceptor : Interceptor {
    private const val MAX_HEIGHT = 1920
    private const val WIDTH = 1080
    private const val X_PADDING: Float = 50f
    private const val Y_PADDING: Float = 25f
    private const val SPACING_MULT: Float = 1f
    private const val SPACING_ADD: Float = 0f
    private const val HEADING_FONT_SIZE: Float = 36f
    private const val BODY_FONT_SIZE: Float = 30f
    private const val SPACING: Float = BODY_FONT_SIZE * SPACING_MULT + SPACING_ADD

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.tag(String::class) != COMMENTS_FLAG) {
            return response
        }

        val paintHeading = TextPaint().apply {
            color = Color.BLACK
            textSize = HEADING_FONT_SIZE
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        @Suppress("DEPRECATION")
        val heading = StaticLayout(
            "章末吐槽：",
            paintHeading,
            (WIDTH - 2 * X_PADDING).toInt(),
            Layout.Alignment.ALIGN_NORMAL,
            SPACING_MULT,
            SPACING_ADD,
            true,
        )

        val comments = parseChapterComments(response)
        val paintBody = TextPaint().apply {
            color = Color.BLACK
            textSize = BODY_FONT_SIZE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        var currentHeight = Y_PADDING + heading.height
        val bodyLayouts = mutableListOf<StaticLayout>()
        for (comment in comments) {
            @Suppress("DEPRECATION")
            val layout = StaticLayout(
                comment,
                paintBody,
                (WIDTH - 2 * X_PADDING).toInt(),
                Layout.Alignment.ALIGN_NORMAL,
                SPACING_MULT,
                SPACING_ADD,
                true,
            )
            val lineHeight = SPACING + layout.height
            // If adding this comment doesn't exceed the max height, add it.
            // If it does exceed and it's a single line, stop adding more comments.
            // Otherwise, if it's multi-line, just skip it and try the next comment.
            if (currentHeight + lineHeight <= MAX_HEIGHT) {
                bodyLayouts.add(layout)
                currentHeight += lineHeight
            } else if (layout.lineCount == 1) {
                break
            }
        }

        // The bitmap height must be no more than MAX_HEIGHT
        // and no less than its width to prevent automatic double-page splitting.
        val bitmapHeight = (currentHeight + Y_PADDING).toInt().coerceIn(WIDTH, MAX_HEIGHT)

        val bitmap = Bitmap.createBitmap(WIDTH, bitmapHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            heading.draw(this, X_PADDING, Y_PADDING)
            var y = Y_PADDING + heading.height + SPACING
            for (layout in bodyLayouts) {
                layout.draw(this, X_PADDING, y)
                y += layout.height + SPACING
            }
        }

        val responseBody = Buffer().run {
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream())
            asResponseBody("image/png".toMediaType())
        }
        return response.newBuilder().body(responseBody).build()
    }

    @Suppress("SameParameterValue")
    private fun StaticLayout.draw(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        this.draw(canvas)
        canvas.restore()
    }
}
