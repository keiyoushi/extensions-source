package eu.kanade.tachiyomi.extension.zh.qinqin

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.network.GET
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Qinqin : SinMH("亲亲漫画", "https://www.acgud.com") {

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/post/?page=$page", headers)

    override fun mangaDetailsParse(document: Document) = mangaDetailsParseDMZJStyle(document, hasBreadcrumb = true)

    override fun Elements.sectionsDescending() = this

    // https://www.acgud.com/js/jmzz20191018.js
    override fun parsePageImages(chapterImages: String): List<String> {
        val key = SecretKeySpec("cxNB23W8xzKJV26O".toByteArray(), "AES")
        val iv = IvParameterSpec("opb4x7z21vg1f3gI".toByteArray())
        val result = Cipher.getInstance("AES/CBC/PKCS7Padding").run {
            init(Cipher.DECRYPT_MODE, key, iv)
            doFinal(Base64.decode(chapterImages, Base64.DEFAULT))
        }
        return super.parsePageImages(String(result))
    }
}
