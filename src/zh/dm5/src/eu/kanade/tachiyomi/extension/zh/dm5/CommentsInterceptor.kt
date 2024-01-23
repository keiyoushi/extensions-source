package eu.kanade.tachiyomi.extension.zh.dm5

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.parser.Parser
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream

// This file is modified from DMZJ extension

val json: Json by injectLazy()

@Serializable
class ChapterCommentDto(
    val PostContent: String,
    val Poster: String,
) {
    override fun toString() = "$Poster: $PostContent"
}

fun parseChapterComments(response: Response): List<String> {
    val result: List<ChapterCommentDto> = json.decodeFromString(response.body.string())
    if (result.isEmpty()) return listOf("没有吐槽")
    return result.map {
        Parser.unescapeEntities(it.toString(), false)
    }
}

object CommentsInterceptor : Interceptor {
    private const val MAX_HEIGHT = 1920
    private const val WIDTH = 1080
    private const val UNIT = 32
    private const val UNIT_F = UNIT.toFloat()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.request.url.toString().contains("pagerdata.ashx")) return response

        val comments = parseChapterComments(response)

        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = UNIT_F
            isAntiAlias = true
        }

        var height = UNIT
        val layouts = comments.map {
            @Suppress("DEPRECATION")
            StaticLayout(it, paint, WIDTH - 2 * UNIT, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }.takeWhile {
            val lineHeight = it.height + UNIT
            if (height + lineHeight <= MAX_HEIGHT) {
                height += lineHeight
                true
            } else {
                false
            }
        }

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)

        var y = UNIT
        for (layout in layouts) {
            canvas.save()
            canvas.translate(UNIT_F, y.toFloat())
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + UNIT
        }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, output)
        val body = output.toByteArray().toResponseBody("image/png".toMediaType())
        return response.newBuilder().body(body).build()
    }
}
