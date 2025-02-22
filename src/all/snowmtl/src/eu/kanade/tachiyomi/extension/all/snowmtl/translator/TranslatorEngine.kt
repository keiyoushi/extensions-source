package eu.kanade.tachiyomi.extension.all.snowmtl.translator
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

interface TranslatorEngine {
    val capacity: Int
    fun translate(from: String, to: String, text: String): String
}
