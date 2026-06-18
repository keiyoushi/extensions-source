package eu.kanade.tachiyomi.extension.ja.mangasaison

import android.util.Base64
import keiyoushi.utils.decodeHex
import keiyoushi.zip.dataRange
import keiyoushi.zip.range
import keiyoushi.zip.readEntry
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.BufferedSource
import okio.buffer
import okio.cipherSource
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty()) return chain.proceed(request)

        val parts = fragment.split(";")
        if (parts.size < 4) return chain.proceed(request)

        val (token, offsetStr, compSizeStr, methodStr) = parts
        val offset = offsetStr.toLong()
        val compressedSize = compSizeStr.toLong()
        val method = methodStr.toInt()

        val key = resolveKey(token)
        // wasm func 217 (LSUZR::getData) AES-CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(IMAGE_IV_HEX))

        val range = dataRange(offset, compressedSize)
        val fileRequest = request.newBuilder().range(range).build()
        val fileResponse = chain.proceed(fileRequest)
        if (!fileResponse.isSuccessful) return fileResponse

        val image = readEntry(fileResponse.body.source(), compressedSize, method).cipherSource(cipher).buffer()
        val mediaType = image.detectImageType()

        return fileResponse.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .body(image.asResponseBody(mediaType))
            .build()
    }

    // LSUZR::new key schedule (wasm func 70)
    private fun resolveKey(token: String): ByteArray {
        val parts = token.split('.') // mdc2.<part1>.<origin>.<contentId>.<sig>
        require(parts.size >= 4) { "wrong mdc2 token format" }
        val part1 = Base64.decode(parts[1], Base64.URL_SAFE) // 56B: ts(4 LE) + 0000 + material(48)
        val origin = Base64.decode(parts[2], Base64.URL_SAFE) // "https://mechacomi.jp"
        val contentId = Base64.decode(parts[3], Base64.URL_SAFE) // e.g. "BT000228921400100101"

        val tsHeader = part1.copyOfRange(0, 8) // only per-token input
        val material = part1.copyOfRange(8, 56) // 3 AES blocks

        // masterKey = SHA-256(tsHeader || origin || contentId || CHUNK4)
        // wasm func 70: func370 x4 (sha update 0x391ad...0x391ce) -> func150 (finalize 0x3921e)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(tsHeader)
        md.update(origin)
        md.update(contentId)
        md.update(CHUNK4)
        val master = md.digest()

        // unwrap with master: AES-256-CBC (wasm func 89 0x3944f)
        val unwrapCipher = Cipher.getInstance("AES/CBC/NoPadding")
        unwrapCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(master, "AES"), IvParameterSpec(ByteArray(16)))
        val unwrapped = unwrapCipher.doFinal(material)

        val imageKey = ByteArray(16) { (unwrapped[16 + it].toInt().inv() and 0xFF).toByte() }
        return imageKey
    }

    private fun BufferedSource.detectImageType(): MediaType {
        val peek = peek()
        val magic = if (peek.request(12L)) peek.readByteArray(12L) else peek.readByteArray()
        return when {
            magic.size >= 3 && magic[0] == 0xFF.toByte() && magic[1] == 0xD8.toByte() && magic[2] == 0xFF.toByte() -> JPEG

            magic.size >= 12 && magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
                magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte() &&
                magic[8] == 'W'.code.toByte() && magic[9] == 'E'.code.toByte() &&
                magic[10] == 'B'.code.toByte() && magic[11] == 'P'.code.toByte() -> WEBP

            magic.size >= 4 && magic[0] == 0x89.toByte() && magic[1] == 'P'.code.toByte() &&
                magic[2] == 'N'.code.toByte() && magic[3] == 'G'.code.toByte() -> PNG

            else -> WEBP
        }
    }

    companion object {
        // static_secret XOR local9 (wasm 1244596 ^ 1244660); the constant 4th SHA-256 block
        private val CHUNK4 = "98b8937ff9fe8aa877d0a0687b90b129940b5e8fbfebd730d62559b1f166fc76".decodeHex()

        // Inside WASM static IV: \06\9a\ba\f7heP\a1\a7\8c\9b\de`\b8\13\e4 -> \06\9a\ba\f7\68\65\50\a1\a7\8c\9b\de\60\b8\13\e4
        private val IMAGE_IV_HEX = "069abaf7686550a1a78c9bde60b813e4".decodeHex()

        private val JPEG = "image/jpeg".toMediaType()
        private val PNG = "image/png".toMediaType()
        private val WEBP = "image/webp".toMediaType()
    }
}
