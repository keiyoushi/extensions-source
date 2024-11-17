package eu.kanade.tachiyomi.extension.pt.randomscan

import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.zipinterceptor.ZipInterceptor
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class LuraZipInterceptor : ZipInterceptor() {
    override fun requestIsZipImage(request: Request): Boolean {
        return request.url.pathSegments.contains("cap-download")
    }

    override fun zipGetByteStream(request: Request, response: Response): InputStream {
        val keyData = listOf("obra_id", "slug", "cap_id", "cap_slug").joinToString("") {
            request.url.queryParameterValues(it).first().toString()
        }.toByteArray(StandardCharsets.UTF_8)
        val encryptedData = response.body.bytes()

        val decryptedData = CryptoAES.decryptFile(encryptedData, keyData, "AES/CTR/NoPadding")
        return ByteArrayInputStream(decryptedData)
    }
}

abstract class LuraToonHttpSource : HttpSource() {
    abstract fun chapterListParse(manga: SManga, response: Response): List<SChapter>

    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException("Not used")
    }
}
