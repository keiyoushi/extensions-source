package eu.kanade.tachiyomi.extension.zh.baozimanhua
// Based on baozibanner by stevenyomi (https://github.com/stevenyomi/baozibanner)

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class BaoziBannerInterceptor(var level: Int) : Interceptor {

    object ReaderPageImageTag

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val currentLevel = this.level
        if (currentLevel == DISABLED) return response

        if (request.tag(ReaderPageImageTag::class.java) == null) return response

        val body = response.body ?: return response
        val contentType = body.contentType() ?: return response
        if (contentType.type != "image") return response

        val host = request.url.host
        val allowed = host.endsWith("baozimh.com") ||
            host.endsWith("baozicdn.com") ||
            host.endsWith("bzcdn.net")
        if (!allowed) return response

        val content = body.bytes()
        val bitmap = BitmapFactory.decodeByteArray(content, 0, content.size)
            ?: return response.newBuilder().body(content.toResponseBody(contentType)).build()

        // Only check when width is large enough for the narrow banner template
        if (bitmap.width < NARROWBANNER_WIDTH) {
            return response.newBuilder().body(content.toResponseBody(contentType)).build()
        }

        val (top, bottom) = BannerChecker.check(bitmap, currentLevel)

        return if (top == 0 && bottom == 0) {
            response.newBuilder().body(content.toResponseBody(contentType)).build()
        } else {
            val result = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bitmap.height - top - bottom)
            val output = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.JPEG, 90, output)
            val responseBody = output.toByteArray().toResponseBody(JPEG_MEDIA_TYPE)
            response.newBuilder().body(responseBody).build()
        }
    }

    companion object {
        const val DISABLED = 0
        const val NORMAL = 1
        const val EXTRA = 2

        const val VERSION_NAME = "2.0"
        const val PREF = "BAOZI_BANNER"
        const val PREF_TITLE = "移除包子漫画横幅 (v$VERSION_NAME)"
        const val PREF_SUMMARY = "已选择：%s\n" +
            "普通模式只能移除上端或下端的一处横幅，强力模式可以移除多个层叠的横幅。" +
            "修改后，已加载的图片需要清除缓存才能生效。"

        val PREF_ENTRIES = arrayOf("禁用", "普通模式", "强力模式")
        val PREF_VALUES = arrayOf("0", "1", "2")

        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
