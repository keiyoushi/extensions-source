package eu.kanade.tachiyomi.extension.all.pornpics

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okio.FileNotFoundException
import okio.IOException
import uy.kohesive.injekt.injectLazy

object JsonFileLoader {
    val json: Json by injectLazy()
    val classLoader = this::class.java.classLoader!!

    inline fun <reified T> loadJsonAs(fileName: String): T {
        val fileContent = classLoader.getResourceAsStream(fileName)
            ?: throw FileNotFoundException("Cannot find JSON file: $fileName")

        try {
            return json.decodeFromStream<T>(fileContent)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Failed to parse JSON file: $fileName", e)
        } catch (e: IOException) {
            throw IOException("Failed to read JSON file: $fileName", e)
        }
    }

    inline fun <reified T> loadLangJsonAs(name: String, lang: String): T {
        val fileName = "assets/i18n/${name}_$lang.json"
        return loadJsonAs<T>(fileName)
    }
}
