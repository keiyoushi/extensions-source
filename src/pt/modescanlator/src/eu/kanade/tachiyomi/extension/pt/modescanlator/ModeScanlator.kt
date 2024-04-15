package eu.kanade.tachiyomi.extension.pt.modescanlator

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms

class ModeScanlator : HeanCms("Mode Scanlator", "https://modescanlator.com", "pt-BR") {

    // PeachScan -> HeanCms
    override val versionId = 2

    override val useNewChapterEndpoint = true
}
