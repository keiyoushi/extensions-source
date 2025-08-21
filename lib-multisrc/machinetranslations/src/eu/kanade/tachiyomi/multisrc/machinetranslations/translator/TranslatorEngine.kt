package eu.kanade.tachiyomi.multisrc.machinetranslations.translator

interface TranslatorEngine {
    val capacity: Int
    fun translate(from: String, to: String, text: String): String
}
