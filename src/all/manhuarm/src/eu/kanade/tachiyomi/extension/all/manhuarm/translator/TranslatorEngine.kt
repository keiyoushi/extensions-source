package eu.kanade.tachiyomi.multisrc.machinetranslations.translator

interface TranslatorEngine {
    fun translate(from: String, to: String, text: String): String
}
