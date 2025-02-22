package eu.kanade.tachiyomi.extension.pt.fenixmanhwas
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import eu.kanade.tachiyomi.multisrc.madara.Madara

class FenixManhwas : Madara(
    "Fênix Manhwas",
    "https://fenixscan.xyz",
    "pt-BR",
) {
    override val versionId = 2
    override val useNewChapterEndpoint = true
}
