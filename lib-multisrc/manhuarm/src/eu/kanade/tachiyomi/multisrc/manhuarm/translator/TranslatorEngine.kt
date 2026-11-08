package eu.kanade.tachiyomi.multisrc.manhuarm.translator

interface TranslatorEngine {
    suspend fun translate(from: String, to: String, text: String): String
}
