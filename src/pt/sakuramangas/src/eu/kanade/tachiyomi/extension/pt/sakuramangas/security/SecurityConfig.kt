package eu.kanade.tachiyomi.extension.pt.sakuramangas.security

import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient

object SecurityConfig {

    const val CLIENT_SIGNATURE = "FTY9K-SY6WY-96LKPM"

    data class Keys(
        val mangaInfo: Long,
        val chapterRead: Long,
        val xVerificationKey1: String,
        val xVerificationKey2: String,
    )

    object SecurityKeyContext {
        const val MANGA_INFO = "manga_info"
        const val CHAPTER_READ = "chapter_read"
    }

    fun extractKeys(baseUrl: String, client: OkHttpClient, headers: Headers): Keys {

        val script = client.newCall(GET("$baseUrl/dist/sakura/global/security.tcp.js", headers))
            .execute().body.string()

        val deobfuscated = Deobfuscator.deobfuscateScript(script)
            ?: throw Error("Failed to deobfuscate security.tcp.js")

        val securityKeySection = deobfuscated.substringAfter("security_keys", "NOT_FOUND")
            .take(300)
        android.util.Log.d("SakuraMangas", "Security keys section: $securityKeySection")

        val mangaInfoRegex = """manga_info:\s*(\d+)""".toRegex()
        val chapterReadRegex = """chapter_read:\s*(\d+)""".toRegex()
        val key1Regex = """key_01:\s*['"]([^'"]+)['"]""".toRegex()
        val key2Regex = """key_02:\s*['"]([^'"]+)['"]""".toRegex()

        val mangaInfoMatch = mangaInfoRegex.find(deobfuscated)
        val chapterReadMatch = chapterReadRegex.find(deobfuscated)
        val key1Match = key1Regex.find(deobfuscated)
        val key2Match = key2Regex.find(deobfuscated)

        var mangaInfo = mangaInfoMatch?.groupValues?.get(1)?.toLongOrNull()
        var chapterRead = chapterReadMatch?.groupValues?.get(1)?.toLongOrNull()
        var key1 = key1Match?.groupValues?.get(1)
        var key2 = key2Match?.groupValues?.get(1)

        mangaInfo?.let {
            val diff = 8106199014741981L - it
        }
        chapterRead?.let {
            val diff = 9007099254140970L - it
        }

        key1 = key1?.let { fixDeobfuscatorStringCorruption(it, 1) }
        key2 = key2?.let { fixDeobfuscatorStringCorruption(it, 2) }
        mangaInfo = mangaInfo?.let { fixDeobfuscatorNumericCorruption(it, 1) }
        chapterRead = chapterRead?.let { fixDeobfuscatorNumericCorruption(it, 2) }

        return Keys(
            mangaInfo = mangaInfo ?: throw Error("Failed to extract manga_info"),
            chapterRead = chapterRead ?: throw Error("Failed to extract chapter_read"),
            xVerificationKey1 = key1 ?: throw Error("Failed to extract key 1"),
            xVerificationKey2 = key2 ?: throw Error("Failed to extract key 2"),
        )
    }

    private fun fixDeobfuscatorStringCorruption(value: String, keyNumber: Int): String {
        return when (keyNumber) {
            1 -> value.replace("-i3j4", "-f3j4")
            2 -> value.replace("z8y8", "z9y8")
            else -> value
        }
    }

    private fun fixDeobfuscatorNumericCorruption(value: Long, keyNumber: Int): Long {
        return when (keyNumber) {
            1 -> value + 1000L
            2 -> value + 100000L
            else -> value
        }
    }

    fun getSecurityKeyForContext(keyName: String?, keys: Keys): Long {
        return when (keyName) {
            SecurityKeyContext.CHAPTER_READ -> keys.chapterRead
            else -> keys.mangaInfo
        }
    }
}
