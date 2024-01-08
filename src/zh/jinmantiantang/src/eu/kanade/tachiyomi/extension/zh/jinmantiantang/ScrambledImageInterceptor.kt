package eu.kanade.tachiyomi.extension.zh.jinmantiantang

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
import kotlin.math.floor

object ScrambledImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val response = chain.proceed(request)
        if (!url.toString().contains("media/photos", ignoreCase = true)) return response // 对非漫画图片连接直接放行
        val pathSegments = url.pathSegments
        val aid = pathSegments[pathSegments.size - 2].toInt()
        if (aid < scrambleId) return response // 对在漫画章节ID为220980之前的图片未进行图片分割,直接放行
// 章节ID:220980(包含)之后的漫画(2020.10.27之后)图片进行了分割getRows倒序处理
        val imgIndex: String = pathSegments.last().substringBefore('.')
        val res = response.body.byteStream().use {
            decodeImage(it, getRows(aid, imgIndex))
        }
        val outputBytes = res.toResponseBody(jpegMediaType)
        return response.newBuilder().body(outputBytes).build()
    }

    // 220980
    // 算法 html页面 1800 行左右
    // 图片开始分割的ID编号
    private const val scrambleId = 220980

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
