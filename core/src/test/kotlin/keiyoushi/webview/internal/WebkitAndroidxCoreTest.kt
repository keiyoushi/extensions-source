package keiyoushi.webview.internal

import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.jar.JarFile

/**
 * lib:webview leaves androidx.core out and ships a stand-in PackageInfoCompat. R8 reports other androidx.core classes
 * as missing, but not a missing member of the stand-in.
 */
class WebkitAndroidxCoreTest {
    @Test
    fun webkitCallsOnlyTheStandInsMember() {
        val jar = JarFile(File(WebViewFeature::class.java.protectionDomain!!.codeSource.location.toURI()))
        val references = jar.use {
            it.entries().asSequence()
                .filter { entry -> entry.name.startsWith("androidx/webkit/") && entry.name.endsWith(".class") }
                .flatMap { entry -> references(DataInputStream(it.getInputStream(entry)), STAND_IN) }
                .toSortedSet()
        }
        assertEquals(setOf("$STAND_IN.getLongVersionCode(Landroid/content/pm/PackageInfo;)J"), references)
    }

    // Members of [owner] referenced from a class file's constant pool
    private fun references(input: DataInputStream, owner: String): List<String> {
        input.skipBytes(8)
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val classes = IntArray(count)
        val members = mutableListOf<Pair<Int, Int>>()
        val nameAndTypes = mutableMapOf<Int, Pair<Int, Int>>()
        var i = 1
        while (i < count) {
            when (input.readUnsignedByte()) {
                1 -> utf8[i] = input.readUTF()
                7 -> classes[i] = input.readUnsignedShort()
                9, 10, 11 -> members += input.readUnsignedShort() to input.readUnsignedShort()
                12 -> nameAndTypes[i] = input.readUnsignedShort() to input.readUnsignedShort()
                3, 4, 17, 18 -> input.skipBytes(4)
                5, 6 -> {
                    input.skipBytes(8)
                    i++
                }
                15 -> input.skipBytes(3)
                8, 16, 19, 20 -> input.skipBytes(2)
                else -> error("Unknown constant pool tag")
            }
            i++
        }
        val classNames = classes.indices.filter { classes[it] != 0 }.associateWith { utf8[classes[it]]!! }
        return members.filter { classNames[it.first] == owner }.map { (_, nameAndType) ->
            val (name, descriptor) = nameAndTypes.getValue(nameAndType)
            "$owner.${utf8[name]}${utf8[descriptor]}"
        }
    }

    private companion object {
        const val STAND_IN = "androidx/core/content/pm/PackageInfoCompat"
    }
}
