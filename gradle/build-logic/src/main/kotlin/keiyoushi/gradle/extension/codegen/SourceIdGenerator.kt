package keiyoushi.gradle.extension.codegen

import java.security.MessageDigest

// Mirrors Mihon's HttpSource.id formula.
fun generateSourceId(name: String, lang: String, versionId: Int): Long {
    val key = "${name.lowercase()}/$lang/$versionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7)
        .map { (bytes[it].toLong() and 0xff) shl (8 * (7 - it)) }
        .reduce(Long::or) and Long.MAX_VALUE
}
