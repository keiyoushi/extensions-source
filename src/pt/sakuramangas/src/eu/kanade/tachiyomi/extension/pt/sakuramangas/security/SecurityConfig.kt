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
        val securityScript: String,
    )

    object SecurityKeyContext {
        const val MANGA_INFO = "manga_info"
        const val CHAPTER_READ = "chapter_read"
    }

    fun extractKeys(baseUrl: String, client: OkHttpClient, headers: Headers): Keys {
        val script = client.newCall(GET("$baseUrl/dist/sakura/global/security.core.js", headers))
            .execute().body.string()

        // Tenta desofuscar, mas se falhar usa o script original
        val deobfuscated = try {
            Deobfuscator.deobfuscateScript(script) ?: script
        } catch (e: Exception) {
            android.util.Log.w("SakuraMangas", "Deobfuscation failed, using original script: ${e.message}")
            script
        }

        android.util.Log.d("SakuraMangas", "Script length: ${script.length}, Deobfuscated length: ${deobfuscated.length}")

        // Regex para formato objeto JavaScript (ex: manga_info: 8106199014740981)
        val mangaInfoObjRegex = """manga_info:\s*(\d+)""".toRegex()
        val chapterReadObjRegex = """chapter_read:\s*(\d+)""".toRegex()
        val key1ObjRegex = """key_01:\s*['"]([^'"]+)['"]""".toRegex()
        val key2ObjRegex = """key_02:\s*['"]([^'"]+)['"]""".toRegex()

        // Regex para formato array (número seguido por 'manga_info' na próxima linha ou mesma)
        // Formato: 8106199014741981,\n'manga_info', ou 8106199014741981,'manga_info'
        val mangaInfoArrRegex = """(\d{13,17}),\s*['"]manga_info['"]""".toRegex()
        val chapterReadArrRegex = """(\d{13,17}),\s*['"]chapter_read['"]""".toRegex()
        val key1ArrRegex = """['"]([a-zA-Z0-9-]+)['"],\s*['"]key_01['"]""".toRegex()
        val key2ArrRegex = """['"]([a-zA-Z0-9-]+)['"],\s*['"]key_02['"]""".toRegex()

        // Tenta encontrar no código desofuscado primeiro, depois no original
        var mangaInfoMatch = mangaInfoObjRegex.find(deobfuscated)
            ?: mangaInfoObjRegex.find(script)
            ?: mangaInfoArrRegex.find(deobfuscated)
            ?: mangaInfoArrRegex.find(script)

        var chapterReadMatch = chapterReadObjRegex.find(deobfuscated)
            ?: chapterReadObjRegex.find(script)
            ?: chapterReadArrRegex.find(deobfuscated)
            ?: chapterReadArrRegex.find(script)

        var key1Match = key1ObjRegex.find(deobfuscated)
            ?: key1ObjRegex.find(script)
            ?: key1ArrRegex.find(deobfuscated)
            ?: key1ArrRegex.find(script)

        var key2Match = key2ObjRegex.find(deobfuscated)
            ?: key2ObjRegex.find(script)
            ?: key2ArrRegex.find(deobfuscated)
            ?: key2ArrRegex.find(script)

        android.util.Log.d("SakuraMangas", "Extracted - mangaInfo: ${mangaInfoMatch?.groupValues?.get(1)}")
        android.util.Log.d("SakuraMangas", "Extracted - chapterRead: ${chapterReadMatch?.groupValues?.get(1)}")
        android.util.Log.d("SakuraMangas", "Extracted - key1: ${key1Match?.groupValues?.get(1)}")
        android.util.Log.d("SakuraMangas", "Extracted - key2: ${key2Match?.groupValues?.get(1)}")

        var mangaInfo = mangaInfoMatch?.groupValues?.get(1)?.toLongOrNull()
        var chapterRead = chapterReadMatch?.groupValues?.get(1)?.toLongOrNull()
        var key1 = key1Match?.groupValues?.get(1)
        var key2 = key2Match?.groupValues?.get(1)

        key1 = key1?.let { fixDeobfuscatorStringCorruption(it, 1) }
        key2 = key2?.let { fixDeobfuscatorStringCorruption(it, 2) }
        mangaInfo = mangaInfo?.let { fixDeobfuscatorNumericCorruption(it, 1) }
        chapterRead = chapterRead?.let { fixDeobfuscatorNumericCorruption(it, 2) }

        return Keys(
            mangaInfo = mangaInfo ?: throw Error("Failed to extract manga_info"),
            chapterRead = chapterRead ?: throw Error("Failed to extract chapter_read"),
            xVerificationKey1 = key1 ?: throw Error("Failed to extract key 1"),
            xVerificationKey2 = key2 ?: throw Error("Failed to extract key 2"),
            securityScript = script,
        )
    }

    private fun fixDeobfuscatorStringCorruption(value: String, keyNumber: Int): String {
        // Os valores no JavaScript atualizado já vêm corretos, sem necessidade de correção
        return value
    }

    private fun fixDeobfuscatorNumericCorruption(value: Long, keyNumber: Int): Long {
        // Os valores no JavaScript atualizado já vêm corretos, sem necessidade de correção
        return value
    }

    fun getSecurityKeyForContext(keyName: String?, keys: Keys): Long {
        return when (keyName) {
            SecurityKeyContext.CHAPTER_READ -> keys.chapterRead
            else -> keys.mangaInfo
        }
    }
}
