package eu.kanade.tachiyomi.extension.en.lhtranslation

import eu.kanade.tachiyomi.multisrc.madara.Madara

class LHTranslation : Madara("LHTranslation", "https://lhtranslation.net", "en") {
    override val versionId = 2
    override val useNewChapterEndpoint = true
}
