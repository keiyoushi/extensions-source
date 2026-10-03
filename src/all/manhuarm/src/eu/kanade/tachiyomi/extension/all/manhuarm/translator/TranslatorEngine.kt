package eu.kanade.tachiyomi.extension.all.manhuarm.translator

interface TranslatorEngine {
    suspend fun translate(from: String, to: String, text: String): String
}
