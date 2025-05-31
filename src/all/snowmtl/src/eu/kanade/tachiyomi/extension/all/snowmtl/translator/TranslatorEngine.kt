package eu.kanade.tachiyomi.extension.all.snowmtl.translator

interface TranslatorEngine {
    val capacity: Int
    fun translate(from: String, to: String, text: String): String
}
