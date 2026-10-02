package eu.kanade.tachiyomi.extension.ar.mangatek

import android.graphics.Typeface
import keiyoushi.utils.applicationContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class FontLoader(private val baseUrl: () -> String, private val id: Long) {

    private val typefaces = ConcurrentHashMap<String, Typeface>()
    private val lock = Any()

    private val cacheDir: File get() =
        applicationContext.cacheDir.resolve("source_$id")
            .apply { mkdirs() }

    fun load(chain: Interceptor.Chain, family: String): Typeface {
        val path = FONT_PATHS[family.toFontKey()] ?: FONT_PATHS[FALLBACK_FAMILY]
            ?: return Typeface.DEFAULT
        typefaces[path]?.let { return it }

        synchronized(lock) {
            typefaces[path]?.let { return it }

            val file = File(cacheDir, path.toCacheName())
            val typeface = file.takeIf { it.length() > 0 }?.toTypeface()
                ?: download(chain, path, file)?.toTypeface()

            if (typeface != null) {
                typefaces[path] = typeface
                return typeface
            }
        }
        return Typeface.DEFAULT
    }

    private fun download(chain: Interceptor.Chain, path: String, target: File): File? {
        return try {
            val request = Request.Builder().url("${baseUrl()}$path".toHttpUrl()).build()
            chain.proceed(request).use { response ->
                if (!response.isSuccessful) return null

                val part = File(target.parentFile, "${target.name}.part")
                response.body.byteStream().use { input ->
                    part.outputStream().use { input.copyTo(it) }
                }
                if (part.renameTo(target)) target else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun File.toTypeface(): Typeface? = try {
        Typeface.createFromFile(this)
    } catch (_: Exception) {
        delete()
        null
    }

    private fun String.toCacheName(): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        val ext = substringBefore("?").substringAfterLast(".")
        return "$hash.$ext"
    }

    companion object {
        private const val FALLBACK_FAMILY = "tajawal"

        private val FONT_PATHS: Map<String, String> = mapOf(
            "Hayah" to "/fonts/manga/66Hayah.otf",
            "Hacen Samra" to "/fonts/manga/Hacen%20Samra%20Lt.ttf",
            "Kharabeesh" to "/fonts/manga/81kharabeesh.ttf",
            "Boahmed Alhour" to "/fonts/manga/Boahmed%20Alhour.ttf",
            "MangaThinking" to "/fonts/manga/thinking.ttf?v=3",
            "Khat Al Taradod" to "/fonts/manga/Khat_Al_Taradod.ttf",
            "ElMessiri" to "/fonts/manga/ElMessiri-SemiBold.ttf",
            "Tajawal" to "/fonts/manga/Tajawal-Medium.ttf",
        ).mapKeys { it.key.toFontKey() }
    }
}

private fun String.toFontKey(): String = substringBefore(",")
    .trim()
    .trim('"', '\'')
    .trim()
    .lowercase()
