// [AI助手声明] 本文件由 AI 助手于 2026-09-22 修改（标记 [FIX-AI]）：
// 在原 GIF 修复版（sourcefix\ScrambledImageInterceptor.kt）基础上，重新合入官方 1.6 新增的
// "_3x4.jpg 封面缩略图不参与分割处理"逻辑；GIF 动图解码问题修复方式与旧修复版一致。
// 修改清单见 G:\MotrixDown\sourcefix\fix\README.md；原版备份在 G:\MotrixDown\sourcefix\backup-original-base\。
package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.ByteArrayInputStream // [FIX-AI] GIF 修复：字节流重建解码入参
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
        if (url.toString().contains("_3x4.jpg", ignoreCase = true)) return response // 封面缩略图不参与分割处理
        val pathSegments = url.pathSegments
        val aid = pathSegments[pathSegments.size - 2].toInt()
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

        // [FIX-AI] GIF 无法正常显示的修复（源自用户旧修复版）：
        // 原版行为: 所有 media/photos 响应都走 BitmapFactory 取首帧 + JPEG 重编码 ——
        // GIF 动图被压成静态图，且尺寸/多帧异常时直接损坏无法显示。
        // 修复后: 先读完字节，嗅探 "GIF" 魔数；GIF 未经站点分割加密，原字节直接透传（保留动画），
        // 非 GIF 才走原有 decodeImage 切片还原。
        val bytes = input.use { it.readBytes() }
        val isGif = bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()

        val (buffer, mediaType) = if (isGif) {
            val buf = Buffer()
            buf.write(bytes)
            Pair(buf, gifMediaType)
        } else {
            Pair(decodeImage(ByteArrayInputStream(bytes), getRows(aid, imgIndex)), jpegMediaType)
        }

        return responseBuilder.body(buffer.asResponseBody(mediaType)).build()
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

    // 对被分割的图片进行分割,排序处理
    private fun decodeImage(img: InputStream, rows: Int): Buffer {
        // 使用bitmap进行图片处理
        val input = BitmapFactory.decodeStream(img)
        // 漫画高度 and width
        val height = input.height
        val width = input.width
        // 未除尽像素
        val remainder = (height % rows)
        // 创建新的图片对象
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // 分割图片
        for (x in 0 until rows) {
            // 分割算法(详情见html源码页的方法"function scramble_image(img)")
            var copyH = floor(height / rows.toDouble()).toInt()
            var py = copyH * x
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
        val buffer = Buffer()
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())

        resultBitmap.recycle()
        input.recycle()

        return buffer
    }

    private val jpegMediaType = "image/jpeg".toMediaType()

    // [FIX-AI] GIF 透传时的媒体类型
    private val gifMediaType = "image/gif".toMediaType()
}
