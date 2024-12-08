package eu.kanade.tachiyomi.extension.all.snowmtl.enginetranslator

interface TranslatorEngine {
    fun translate(from: String, to: String, text: String): String
}
