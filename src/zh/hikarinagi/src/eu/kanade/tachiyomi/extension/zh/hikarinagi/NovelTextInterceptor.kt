package eu.kanade.tachiyomi.extension.zh.hikarinagi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

/**
 * Renders a page of novel text into an image for continuous (vertical) reading.
 *
 * The vertical layout is driven by baselines: paragraphs are stacked a line plus one paragraph gap
 * apart, so the spacing between paragraphs is exactly one line spacing plus [PARAGRAPH_GAP] instead
 * of whatever the text engine happens to report. A page ends after the last line's descent plus the
 * same paragraph gap, which is what makes stacked pages continue without a seam.
 *
 * Lines are justified to the right margin: the leftover space of a line is spread between its
 * characters (or between its words when the line contains spaces), leaving only the last line of a
 * paragraph short.
 */
class NovelTextInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)

        val bitmap = render(
            url.pathSegments.getOrNull(0).orEmpty(),
            url.pathSegments.getOrNull(1).orEmpty(),
            url.queryParameter("top") == "1",
        )

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body(stream.toByteArray().toResponseBody(IMAGE_PNG))
            .build()
    }

    private fun render(title: String, text: String, topPadding: Boolean): Bitmap {
        val heading = title.takeIf { it.isNotBlank() }?.let(::headingLayout)
        val paragraphs = text.split('\n').filter(String::isNotBlank).map(::bodyLayout)

        val headingBlock = heading?.let { it.height + 2 * HEADING_GAP + DIVIDER_HEIGHT } ?: 0
        // Pages that continue a running text stay flush with the page above; pages that start after
        // an illustration (or a chapter) get the same breathing room as the space above a heading.
        val topMargin = if (heading != null || topPadding) TOP_MARGIN else 0
        val firstBaseline = (topMargin + headingBlock) - bodyPaint.ascent()
        var lastBaseline = firstBaseline
        paragraphs.forEachIndexed { index, layout ->
            if (index > 0) lastBaseline += LINE_HEIGHT + PARAGRAPH_GAP
            lastBaseline += baselineSpan(layout)
        }

        val height = (lastBaseline + LINE_HEIGHT + PARAGRAPH_GAP + bodyPaint.ascent()).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        heading?.let {
            canvas.save()
            canvas.translate(X_PADDING, topMargin.toFloat())
            it.draw(canvas)
            canvas.restore()

            val dividerY = (topMargin + it.height + HEADING_GAP).toFloat()
            canvas.drawRect(X_PADDING, dividerY, WIDTH - X_PADDING, dividerY + DIVIDER_HEIGHT, dividerPaint)
        }

        var baseline = firstBaseline
        paragraphs.forEachIndexed { index, layout ->
            if (index > 0) baseline += LINE_HEIGHT + PARAGRAPH_GAP
            drawParagraph(canvas, layout, baseline)
            baseline += baselineSpan(layout)
        }

        return bitmap
    }

    /** Distance between the first and the last baseline of a paragraph. */
    private fun baselineSpan(layout: StaticLayout) = (layout.getLineBaseline(layout.lineCount - 1) - layout.getLineBaseline(0)).toFloat()

    private fun drawParagraph(canvas: Canvas, layout: StaticLayout, firstBaseline: Float) {
        val first = layout.getLineBaseline(0)
        val lastLine = layout.lineCount - 1
        for (line in 0..lastLine) {
            val baseline = firstBaseline + (layout.getLineBaseline(line) - first)
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line)
            val lineText = layout.text.subSequence(start, end).toString().trimEnd('\n')
            if (lineText.isEmpty()) continue

            // A closing mark is placed so that its ink meets the margin: the full width box of a
            // mark leaves half a character of paper white at the end of a line, and stretching the
            // characters in front of it to close that gap is the part of justification to avoid.
            val mark = lineText.takeIf { it.length > 1 }?.last()?.takeIf { it in HANGING_MARKS }
            val body = if (mark == null) lineText else lineText.dropLast(1)
            val markX = X_PADDING + CONTENT_WIDTH - inkRight(lineText)
            val width = if (mark == null) CONTENT_WIDTH else (markX - X_PADDING).toInt()

            // The last line of a paragraph stays short, like in print.
            if (line == lastLine || width <= 0 || !drawJustified(canvas, body, baseline, width)) {
                canvas.drawText(lineText, X_PADDING, baseline, bodyPaint)
            } else if (mark != null) {
                canvas.drawText(lineText, lineText.length - 1, lineText.length, markX, baseline, bodyPaint)
            }
        }
    }

    /** Distance from the start of the last character of [text] to the right edge of its ink. */
    private fun inkRight(text: String): Float {
        val bounds = Rect()
        bodyPaint.getTextBounds(text, text.length - 1, text.length, bounds)
        return bounds.right.toFloat()
    }

    /** Stretches a line onto [width]; returns false when the line is better left aligned. */
    private fun drawJustified(canvas: Canvas, line: String, baseline: Float, width: Int): Boolean {
        val natural = bodyPaint.measureText(line)
        if (natural <= 0f) return false
        val slack = width - natural
        if (slack < JUSTIFY_MIN_SLACK || slack > JUSTIFY_MAX_SLACK) return false

        // Scaling the shaped run, rather than spacing the characters by hand, keeps the line on the
        // margin when the font renders a run narrower than its characters (「」, ……, —— and friends).
        canvas.save()
        canvas.translate(X_PADDING, baseline)
        canvas.scale(width / natural, 1f)
        canvas.drawText(line, 0f, 0f, bodyPaint)
        canvas.restore()
        return true
    }

    companion object {
        private const val HOST = "hikarinagi-novel-text"

        private const val WIDTH = 1000
        private const val X_PADDING = 60f
        private const val CONTENT_WIDTH = (WIDTH - 2 * X_PADDING).toInt()
        private const val BODY_SIZE = 40f
        private const val HEADING_SIZE = 56f
        private const val LINE_SPACING_MULT = 1.5f
        private const val LINE_SPACING_EXTRA = 6f

        /** Extra space between two paragraphs, on top of one line spacing. */
        private const val PARAGRAPH_GAP = 25

        /** Space above the heading. */
        private const val TOP_MARGIN = 60

        /** Space between the heading, the divider and the first line of text. */
        private const val HEADING_GAP = 48

        private const val DIVIDER_HEIGHT = 2
        private const val INDENT = "　"
        private const val BACKGROUND = Color.WHITE
        private const val DIVIDER_COLOR = "#DDDDDD"
        private const val JUSTIFY_MIN_SLACK = 2f
        private const val JUSTIFY_MAX_SLACK = 2 * BODY_SIZE

        /**
         * Marks whose ink leaves paper white in front of them when they end a line, so their ink
         * (not their box) is aligned to the margin. Point marks plus the closing marks, and the
         * Chinese ！？: which sit left of centre in their box.
         */
        private const val HANGING_MARKS = "，。、；：！？,.;:!?）〕］｝〉》」』】〗”’"

        private val IMAGE_PNG = "image/png".toMediaType()

        private val headingPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = HEADING_SIZE
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        private val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = BODY_SIZE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        private val dividerPaint = Paint().apply {
            color = Color.parseColor(DIVIDER_COLOR)
            isAntiAlias = true
        }

        private val LINE_HEIGHT = ((bodyPaint.descent() - bodyPaint.ascent()) * LINE_SPACING_MULT + LINE_SPACING_EXTRA).toInt()

        fun createUrl(title: String, text: String, topPadding: Boolean = false): String = "http://$HOST/${Uri.encode(title)}/${Uri.encode(text)}" + if (topPadding) "?top=1" else ""

        private fun headingLayout(title: String) = StaticLayout.Builder
            .obtain(title, 0, title.length, headingPaint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()

        private fun bodyLayout(paragraph: String): StaticLayout {
            // A single ideographic space indents the first line of a paragraph.
            val indented = INDENT + paragraph
            return StaticLayout.Builder.obtain(indented, 0, indented.length, bodyPaint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(LINE_SPACING_EXTRA, LINE_SPACING_MULT)
                .setIncludePad(false)
                .build()
        }
    }
}
