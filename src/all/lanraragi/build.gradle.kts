import java.security.MessageDigest

plugins {
    alias(kei.plugins.extension)
}

// Retain previous IDs precisely so users don't lose migrated manga
fun getLrrId(suffix: String): Long {
    val key = "lanraragi" + (if (suffix == "1") "" else "_$suffix") + "/all/1"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}

keiyoushi {
    name = "LANraragi"
    versionCode = 22
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "LANraragi"
        lang = "all"
        baseUrl("http://127.0.0.1:3000") {
            withCustom = true
        }
        id = getLrrId("1")
    }

    source {
        name = "LANraragi 2"
        lang = "all"
        baseUrl("http://127.0.0.1:3000") {
            withCustom = true
        }
        id = getLrrId("2")
    }
}
