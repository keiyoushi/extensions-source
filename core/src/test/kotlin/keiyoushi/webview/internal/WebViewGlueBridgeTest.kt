package keiyoushi.webview.internal

import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.jar.JarFile

/** Core's copies of Chromium's boundary interfaces and names, against the ones androidx.webkit ships. */
class WebViewGlueBridgeTest {
    private val webkitJar = File(WebViewFeature::class.java.protectionDomain!!.codeSource.location.toURI())
    private val coreClasses = File(WebViewGlueBridge::class.java.protectionDomain!!.codeSource.location.toURI())

    // webkit's own classes first, so its boundary interfaces aren't core's copies of the same name
    private val webkit = object : URLClassLoader(arrayOf(webkitJar.toURI().toURL()), javaClass.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
            if (name.startsWith("org.chromium.") || name.startsWith("androidx.webkit.")) {
                findLoadedClass(name) ?: findClass(name)
            } else {
                super.loadClass(name, resolve)
            }
        }
    }

    // Chromium looks each method up on the interface that declares it, by name and parameter types
    @Test
    fun boundaryInterfacesMatchWebkit() {
        val mismatches = classNames(coreClasses, "org/chromium/support_lib_boundary/").flatMap { name ->
            val copy = Class.forName(name)
            val declared = webkit.loadClass(name).declaredMethods.map { it.signature() }.toSet()
            copy.declaredMethods.filterNot { it.isSynthetic }.map { it.signature() }.filter { it !in declared }.map { "$name.$it" }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun featureAndMetadataNamesExist() {
        val known = constants("org.chromium.support_lib_boundary.util.Features") +
            constants("androidx.webkit.internal.UserAgentMetadataInternal")
        val used = classNames(coreClasses, "keiyoushi/webview/internal/WebViewGlueBridge")
            .flatMap { stringConstants(classBytes(coreClasses, it)) }
            .filter { NAME_REGEX.matches(it) }
            .toSortedSet()
        assertEquals(emptySet<String>(), used - known)
    }

    // The JVM's android.jar has no WebView.getFactory(), like Suwayomi
    @Test
    fun noGlueMeansUnsupported() {
        assertFalse(WebViewGlueBridge.isFeatureSupported("MUTE_AUDIO"))
    }

    @Test
    fun reducedChromeUaKeepsEngineVersions() {
        val hints = clientHintsFor(REDUCED_UA, engine)!!

        @Suppress("UNCHECKED_CAST")
        val brands = hints["BRAND_VERSION_LIST"] as Array<Array<String>>
        assertEquals(listOf("Not:A-Brand", "Chromium", "Google Chrome"), brands.map { it[0] })
        assertEquals(listOf("8", "156", "156"), brands.map { it[1] })
        assertEquals("156.0.8078.47", hints["FULL_VERSION"])
        assertEquals("156.0.8078.47", brands[2][2])
        assertEquals("11.0.0", hints["PLATFORM_VERSION"])
        assertEquals("Android", hints["PLATFORM"])
        assertEquals(true, hints["MOBILE"])
        assertEquals("ONEPLUS A6003", hints["MODEL"])
    }

    @Test
    fun otherChromeVersionUsesTheUaVersion() {
        val hints = clientHintsFor(WINDOWS_UA, engine)!!
        assertEquals("150.0.7000.1", hints["FULL_VERSION"])
        assertEquals("Windows", hints["PLATFORM"])
        assertEquals("19.0.0", hints["PLATFORM_VERSION"])
        assertEquals("x86", hints["ARCHITECTURE"])
        assertEquals(false, hints["MOBILE"])
    }

    @Test
    fun notChrome() {
        assertNull(clientHintsFor("Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0", engine))
    }

    private val engine: Map<String, Any> = mapOf(
        "BRAND_VERSION_LIST" to arrayOf(
            arrayOf("Not:A-Brand", "8", "8.0.0.0"),
            arrayOf("Chromium", "156", "156.0.8078.47"),
            arrayOf("Android WebView", "156", "156.0.8078.47"),
        ),
        "FULL_VERSION" to "156.0.8078.47",
        "PLATFORM" to "Android",
        "PLATFORM_VERSION" to "11.0.0",
        "ARCHITECTURE" to "",
        "MODEL" to "ONEPLUS A6003",
        "MOBILE" to true,
    )

    private fun java.lang.reflect.Method.signature() = "$name(${parameterTypes.joinToString { it.name }}): ${returnType.name}"

    // The String constants a class declares, private ones included
    private fun constants(className: String): Set<String> = webkit.loadClass(className).declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.apply { isAccessible = true }.get(null) as String }
        .toSet()

    // Binary names of the classes under [prefix] in a class directory or jar
    private fun classNames(root: File, prefix: String): List<String> {
        val paths = if (root.isDirectory) {
            root.walk().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        } else {
            JarFile(root).use { jar -> jar.entries().asSequence().map { it.name }.toList() }
        }
        return paths.filter { it.startsWith(prefix) && it.endsWith(".class") }.map { it.removeSuffix(".class").replace('/', '.') }
    }

    private fun classBytes(root: File, className: String): ByteArray {
        val path = className.replace('.', '/') + ".class"
        if (root.isDirectory) return root.resolve(path).readBytes()
        return JarFile(root).use { it.getInputStream(it.getEntry(path)).readBytes() }
    }

    // CONSTANT_String values from a class file's constant pool; the source would also give annotation arguments
    private fun stringConstants(bytes: ByteArray): List<String> {
        val input = DataInputStream(bytes.inputStream())
        input.skipBytes(8)
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val strings = mutableListOf<Int>()
        var i = 1
        while (i < count) {
            when (input.readUnsignedByte()) {
                1 -> utf8[i] = input.readUTF()
                8 -> strings += input.readUnsignedShort()
                7, 16, 19, 20 -> input.skipBytes(2)
                15 -> input.skipBytes(3)
                3, 4, 9, 10, 11, 12, 17, 18 -> input.skipBytes(4)
                5, 6 -> {
                    input.skipBytes(8)
                    i++
                }
                else -> error("Unknown constant pool tag")
            }
            i++
        }
        return strings.mapNotNull { utf8[it] }
    }

    private companion object {
        // Feature names (with an optional version suffix) and metadata keys
        const val REDUCED_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Mobile Safari/537.36"
        const val WINDOWS_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.7000.1 Safari/537.36"

        val NAME_REGEX = Regex("""[A-Z][A-Z0-9]*(_[A-Z0-9]+)+(:\d+)?|MOBILE|PLATFORM""")
    }
}
