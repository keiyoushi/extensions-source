package io.github.keiyoushi.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Hashes what an extension ships, independent of its version and signature, so CI can tell whether
 * a rebuild changed anything.
 */
@CacheableTask
abstract class ComputeContentHashTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extensionJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun compute() {
        val digest = MessageDigest.getInstance("SHA-256")
        fun update(name: String, bytes: ByteArray) {
            digest.update(name.toByteArray())
            digest.update(0)
            digest.update(bytes.size.toString().toByteArray())
            digest.update(0)
            digest.update(bytes)
        }

        val manifest = manifestFile.get().asFile.readText().replace(VERSION_ATTRIBUTE, "")
        update("manifest", manifest.toByteArray())

        val apk = apkDir.get().asFile.walkTopDown().first { it.extension == "apk" }
        // The manifests carry the version; their content is covered by the merged manifest above.
        // The apk is signed outside its entries (v2+), and the jar is hashed before signing.
        apk.forEachEntry({ it != "AndroidManifest.xml" }) { name, bytes ->
            update("apk/$name", if (DEX_ENTRY.matches(name)) normalizeDex(bytes) else bytes)
        }
        extensionJar.get().asFile.forEachEntry({ it != "AndroidManifest.xml" }) { name, bytes ->
            update("jar/$name", bytes)
        }

        outputFile.get().asFile.writeText(digest.digest().joinToString("") { "%02x".format(it) })
    }
}

private val VERSION_ATTRIBUTE = Regex("""\s+android:version(Code|Name)="[^"]*"""")
private val DEX_ENTRY = Regex("""classes\d*\.dex""")

// R8 embeds a hash of mapping.txt as each class's SourceFile and in its marker string, so it changes
// whenever a line number shifts in inlined code (e.g. an import added to a core file).
private val MAP_ID = Regex("""(r8-map-id-|"pg-map-id":")[0-9a-f]{64}""")

private fun normalizeDex(dex: ByteArray): ByteArray {
    // ISO-8859-1 maps bytes 1:1, so the masked ids keep their length and offsets
    val text = String(dex, Charsets.ISO_8859_1).replace(MAP_ID) { it.groupValues[1] + "0".repeat(64) }
    // Skip the header checksum and signature (bytes 8..31), which cover the masked ids
    return text.toByteArray(Charsets.ISO_8859_1).copyOfRange(32, dex.size)
}

private fun File.forEachEntry(filter: (String) -> Boolean, action: (String, ByteArray) -> Unit) {
    ZipFile(this).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory && filter(it.name) }
            .sortedBy { it.name }
            .forEach { action(it.name, zip.getInputStream(it).use { input -> input.readBytes() }) }
    }
}
