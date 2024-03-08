package eu.kanade.tachiyomi.extension.id.mirrordesu

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.jsoup.nodes.Document

class MirrorDesu : MangaThemesia(
    "MirrorDesu",
    "https://mirrordesu.one",
    "id",
    "/komik",
) {
    override val client = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(ts_reader)")?.data()
            ?: return super.pageListParse(document)

        val deobfuscatedScript = Deobfuscator.deobfuscateScript(script)!!

        val (dataKey, pwdKey) = obfuscatedNamesRegex.find(deobfuscatedScript)!!
            .groupValues.let { it[1] to it[2] }

        val encData = deobfuscatedScript.substringAfter(dataKey)
            .substringAfter("\'")
            .substringBefore("\'")
            .let { json.decodeFromString<EncryptedDto>(it) }

        val unsaltedCiphertext = Base64.decode(encData.cipherText, Base64.DEFAULT)
        val salt = encData.salt.decodeHex()
        val ciphertext = Base64.encodeToString(salted + salt + unsaltedCiphertext, Base64.DEFAULT)

        val pwd = Regex("""let\s*$pwdKey\s*=\s*'(\w+)'""").find(deobfuscatedScript)!!
            .groupValues[1]

        val data = CryptoAES.decrypt(ciphertext, pwd)

        val tsReader = json.decodeFromString<TSReader>(json.decodeFromString<String>(data))
        val imageUrls = tsReader.sources.firstOrNull()?.images ?: return emptyList()
        return imageUrls.mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl) }
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private val salted = "Salted__".toByteArray(Charsets.UTF_8)

    private val obfuscatedNamesRegex = Regex("""CryptoJSAesJson\.decrypt\(\s*(\w+)\s*,\s*(\w+)\s*\)""")

    @Serializable
    class EncryptedDto(
        @SerialName("ct") val cipherText: String,
        @SerialName("s") val salt: String,
    )

    @Serializable
    class TSReader(
        val sources: List<ReaderImageSource>,
    )

    @Serializable
    class ReaderImageSource(
        val images: List<String>,
    )
}
