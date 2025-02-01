package eu.kanade.tachiyomi.extension.pt.fenixmanhwas
import eu.kanade.tachiyomi.multisrc.madara.Madara

class FenixManhwas : Madara(
    "Fênix Manhwas",
    "https://fenixscan.xyz",
    "pt-BR",
) {
    override val versionId = 2
    override val useNewChapterEndpoint = true
}
