package eu.kanade.tachiyomi.extension.pt.modescanlator

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms

class ModeScanlator : HeanCms(
    "Mode Scanlator",
    "https://site.modescanlator.net",
    "pt-BR",
    "https://api.modescanlator.net",
) {

    // PeachScan -> HeanCms
    override val versionId = 2

    override val useNewChapterEndpoint = true
}
