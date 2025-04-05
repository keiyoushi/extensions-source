package eu.kanade.tachiyomi.extension.all.pornpics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import uy.kohesive.injekt.injectLazy

object JsonFileLoader {

    val json: Json by injectLazy()

    inline fun <reified T> loadJsonAs(fileName: String): T {
        val classLoader = this::class.java.classLoader!!
        val fileContent = classLoader.getResourceAsStream(fileName)

        return json.decodeFromStream<T>(fileContent)
    }

    inline fun <reified T> loadLangJsonAs(name: String, lang: String): T {
        val classLoader = this::class.java.classLoader!!
        val fileName = "assets/i18n/${name}_$lang.json"
        return loadJsonAs<T>(fileName)
    }
}
