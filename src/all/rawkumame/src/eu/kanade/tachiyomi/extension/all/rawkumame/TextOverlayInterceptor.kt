package eu.kanade.tachiyomi.extension.all.rawkumame

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class TextOverlayInterceptor(private val baseUrl: () -> String) : Interceptor {

    private val typefaces = ConcurrentHashMap<String, Typeface>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val overlay = request.url.fragment?.parseAs<PageOverlay>() ?: return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val options = BitmapFactory.Options().apply { inMutable = true }
        val bitmap = response.body.byteStream().use { BitmapFactory.decodeStream(it, null, options) }!!
        val canvas = Canvas(bitmap)

        // svg coordinates are in the page size from the payload, the served image can be smaller
        canvas.scale(bitmap.width / overlay.width, bitmap.height / overlay.height)

        Jsoup.parse(overlay.svg, "", Parser.xmlParser()).children().forEach { element ->
            when (element.tagName()) {
                "rect" -> canvas.drawBox(element)
                "g" -> canvas.drawBlock(element, chain)
            }
        }

        val output = Buffer()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output.outputStream())
        bitmap.recycle()

        return response.newBuilder()
            .body(output.asResponseBody(JPEG, output.size))
            .build()
    }

    private fun Canvas.drawBox(rect: Element) {
        val left = rect.attr("x").toFloat()
        val top = rect.attr("y").toFloat()
        val bounds = RectF(left, top, left + rect.attr("width").toFloat(), top + rect.attr("height").toFloat())
        val radius = rect.attr("rx").toFloatOrNull() ?: 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        rect.attr("fill").takeIf { it.isNotEmpty() }?.let {
            paint.color = it.toColor()
            drawRoundRect(bounds, radius, radius, paint)
        }
        rect.attr("stroke").takeIf { it.isNotEmpty() }?.let {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = rect.attr("stroke-width").toFloatOrNull() ?: 1f
            paint.color = it.toColor()
            drawRoundRect(bounds, radius, radius, paint)
        }
    }

    private fun Canvas.drawBlock(group: Element, chain: Interceptor.Chain) {
        val css = group.attr("style").split(';')
            .map { it.split(':', limit = 2) }
            .filter { it.size == 2 }
            .associate { (key, value) -> key.trim() to value.trim() }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = chain.typeface(css["font-family"].orEmpty().substringBefore(',').trim('"', '\'', ' '))
            textSize = css.getValue("font-size").removeSuffix("px").toFloat()
            color = css["fill"]?.toColor() ?: Color.BLACK
        }
        val stroke = css["stroke"]?.let { strokeColor ->
            Paint(fill).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeWidth = css["stroke-width"]?.removeSuffix("px")?.toFloatOrNull() ?: 1f
                color = strokeColor.toColor()
            }
        }

        group.select("text").forEach { text ->
            val content = text.text()
            if (content.isBlank()) return@forEach
            val x = text.attr("x").toFloat()
            val y = text.attr("y").toFloat()
            val length = text.attr("textLength").toFloatOrNull()

            listOfNotNull(stroke, fill).forEach { paint ->
                paint.textScaleX = 1f
                // mimics the svg length adjust, each line is stretched or squeezed to exactly its given text length
                if (length != null) paint.textScaleX = length / paint.measureText(content)
                drawText(content, x, y, paint)
            }
        }
    }

    private fun Interceptor.Chain.typeface(family: String): Typeface {
        typefaces[family]?.let { return it }
        // the site serves its reader fonts from /media/_fonts/, the chapter payload lists which ones it uses
        val request = request().newBuilder().url("${baseUrl()}/media/_fonts/$family.ttf").build()
        val typeface = try {
            proceed(request).use { response ->
                if (!response.isSuccessful) return Typeface.DEFAULT_BOLD
                val file = File.createTempFile(family, ".ttf")
                response.body.byteStream().use { input -> file.outputStream().use(input::copyTo) }
                Typeface.createFromFile(file).also { file.delete() }
            }
        } catch (_: IOException) {
            return Typeface.DEFAULT_BOLD
        }
        typefaces[family] = typeface
        return typeface
    }

    private fun String.toColor(): Int = when {
        startsWith("rgb(") -> removePrefix("rgb(").removeSuffix(")").split(',')
            .map { it.trim().toInt() }
            .let { (r, g, b) -> Color.rgb(r, g, b) }
        startsWith("#") && length == 4 -> Color.parseColor("#" + drop(1).map { "$it$it" }.joinToString(""))
        else -> Color.parseColor(this)
    }

    companion object {
        private val JPEG = "image/jpeg".toMediaType()
    }
}
