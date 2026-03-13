package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.math.floor

object ScrambledImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val response = chain.proceed(request)
        if (!url.toString().contains("media/photos", ignoreCase = true)) return response // 对非漫画图片连接直接放行

        val fileName = url.pathSegments.lastOrNull().orEmpty()
        val contentType = response.header("Content-Type").orEmpty()

        // 动图（GIF）不能走 Bitmap -> JPEG 重编码，否则会丢失动画帧
        if (isGif(fileName, contentType)) return response

        // 动图 WebP 同样不能重编码；仅在可确认是动画 WebP 时跳过
        if (isAnimatedWebp(fileName, contentType, response)) return response

        val pathSegments = url.pathSegments
        val aid = pathSegments.getOrNull(pathSegments.size - 2)?.toIntOrNull() ?: return response
        if (aid < SCRAMBLE_ID) return response // 对在漫画章节ID为220980之前的图片未进行图片分割,直接放行
        // 章节ID:220980(包含)之后的漫画(2020.10.27之后)图片进行了分割getRows倒序处理
        val responseBuilder = response.newBuilder()
        val imgIndex: String = pathSegments.last().substringBefore('.')
        val input = if ("gzip" == response.header("Content-Encoding")) {
            responseBuilder.headers(
                response.headers.newBuilder()
                    .removeAll("Content-Encoding")
                    .removeAll("Content-Length")
                    .build(),
            )
            GZIPInputStream(response.body.byteStream())
        } else {
            response.body.byteStream()
        }
        val newBody = input.use {
            decodeImage(it, getRows(aid, imgIndex))
        }.toResponseBody(jpegMediaType)
        return responseBuilder.body(newBody).build()
    }

    // 220980
    // 算法 html页面 1800 行左右
    // 图片开始分割的ID编号
    private const val SCRAMBLE_ID = 220980

    private fun md5LastCharCode(input: String): Int {
        val md5 = MessageDigest.getInstance("MD5")
        val lastByte = md5.digest(input.toByteArray()).last().toInt() and 0xFF
        return lastByte.toString(16).last().code
    }

    private fun getRows(aid: Int, imgIndex: String): Int {
        val modulus = when {
            aid >= 421926 -> 8
            aid >= 268850 -> 10
            else -> return 10
        }
        return 2 * (md5LastCharCode(aid.toString() + imgIndex) % modulus) + 2
    }

    private fun isGif(fileName: String, contentType: String): Boolean = fileName.endsWith(".gif", ignoreCase = true) ||
        contentType.contains("image/gif", ignoreCase = true)

    private fun isAnimatedWebp(fileName: String, contentType: String, response: Response): Boolean {
        val maybeWebp = fileName.endsWith(".webp", ignoreCase = true) ||
            contentType.contains("image/webp", ignoreCase = true)
        if (!maybeWebp) return false

        // 若响应体被 gzip 压缩，peek 到的是压缩字节，保守地继续走原逻辑（避免误判影响静态 webp）
        if (response.header("Content-Encoding").equals("gzip", ignoreCase = true)) return false

        val head = try {
            response.peekBody(64 * 1024).bytes()
        } catch (_: Exception) {
            return false
        }
        return looksLikeAnimatedWebp(head)
    }

    private fun looksLikeAnimatedWebp(data: ByteArray): Boolean {
        if (data.size < 16) return false
        if (!(data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() && data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte())) return false
        if (!(data[8] == 'W'.code.toByte() && data[9] == 'E'.code.toByte() && data[10] == 'B'.code.toByte() && data[11] == 'P'.code.toByte())) return false

        // 1) 直接查找 ANIM chunk（最可靠）
        val anim = byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'I'.code.toByte(), 'M'.code.toByte())
        for (i in 12..(data.size - anim.size)) {
            var matched = true
            for (j in anim.indices) {
                if (data[i + j] != anim[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }

        // 2) VP8X 扩展头动画标志位（bit 1）
        // chunk layout: [12..15] = FourCC, [16..19] = chunk size, [20] = flags
        if (data.size > 21 &&
            data[12] == 'V'.code.toByte() && data[13] == 'P'.code.toByte() && data[14] == '8'.code.toByte() && data[15] == 'X'.code.toByte()
        ) {
            val flags = data[20].toInt() and 0xFF
            if ((flags and 0x02) != 0) return true
        }

        return false
    }

    // 对被分割的图片进行分割,排序处理
    private fun decodeImage(img: InputStream, rows: Int): ByteArray {
        // 使用bitmap进行图片处理
        val input = BitmapFactory.decodeStream(img)
        // 漫画高度 and width
        val height = input.height
        val width = input.width
        // 未除尽像素
        val remainder = (height % rows)
        // 创建新的图片对象
        val resultBitmap = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        // 分割图片
        for (x in 0 until rows) {
            // 分割算法(详情见html源码页的方法"function scramble_image(img)")
            var copyH = floor(height / rows.toDouble()).toInt()
            var py = copyH * (x)
            val y = height - (copyH * (x + 1)) - remainder
            if (x == 0) {
                copyH += remainder
            } else {
                py += remainder
            }
            // 要裁剪的区域
            val crop = Rect(0, y, width, y + copyH)
            // 裁剪后应放置到新图片对象的区域
            val splic = Rect(0, py, width, py + copyH)

            canvas.drawBitmap(input, crop, splic, null)
        }
        // 创建输出流
        val output = ByteArrayOutputStream()
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        return output.toByteArray()
    }

    private val jpegMediaType = "image/jpeg".toMediaType()
}
