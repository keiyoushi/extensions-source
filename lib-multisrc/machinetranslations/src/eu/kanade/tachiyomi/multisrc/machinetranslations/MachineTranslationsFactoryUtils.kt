package eu.kanade.tachiyomi.multisrc.machinetranslations
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

class MachineTranslationsFactoryUtils

interface Language {
    val lang: String
    val target: String
    val origin: String
    var fontSize: Int
    var disableSourceSettings: Boolean
}

data class LanguageImpl(
    override val lang: String,
    override val target: String = lang,
    override val origin: String = "en",
    override var fontSize: Int = 24,
    override var disableSourceSettings: Boolean = false,
) : Language
